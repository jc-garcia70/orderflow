# Especificación de Eventos y Contratos (Fase 0)

Este documento define la arquitectura orientada a eventos (**Event-Driven Architecture**) y el patrón **Saga Coreografiada** utilizado en OrderFlow para coordinar transacciones de negocio entre los microservicios sin acoplamiento síncrono.

---

## 1. Topología de Tópicos Kafka

| Tópico | Productor Principal | Consumidores | Particionamiento (`Key`) |
| :--- | :--- | :--- | :--- |
| `order-events` | `orders-service` | `inventory-service`, `notification-service` | `orderId` |
| `inventory-events` | `inventory-service` | `orders-service` | `orderId` |

> **Garantía de orden:** Al utilizar `orderId` como clave de partición (`Kafka Message Key`), se garantiza que todos los eventos relativos a un mismo pedido lleguen estrictamente en orden a la misma partición.

### Outbox, reintentos y entrega

`orders-service` persiste cada evento de pedido en `order_outbox` dentro de la misma transacción que crea o cambia la orden. Un relay publica posteriormente los registros pendientes en Kafka. Los envíos confirmados se marcan como publicados; los fallidos conservan el evento y se reintentan con backoff exponencial. Los registros publicados se conservan durante un periodo configurable antes de su limpieza.

El relay proporciona entrega **al menos una vez**: un fallo del proceso después de que Kafka acepte un mensaje, pero antes de marcarlo como publicado en la base de datos, puede producir una entrega duplicada. Por eso los consumidores deben ser idempotentes y usar `eventId`/`aggregateId` para reconocer mensajes repetidos.

El consumidor de `orders-service` reintenta fallos transitorios de `inventory-events` con backoff exponencial. Tras agotar los reintentos, el mensaje se publica en `inventory-events.DLT` para inspección y reprocesamiento operativo. El consumidor de `inventory-service` aplica la misma política a `order-events` y enruta los casos agotados a `order-events.DLT`. Los mensajes malformados también se enrutan a la DLT correspondiente.

`inventory-service` registra cada `orderId` cuya reserva inicial ya procesó. Una redelivery del mismo `OrderCreatedEvent` no vuelve a descontar/reservar el inventario. El registro y los cambios de stock participan en la misma transacción local. La publicación de `StockReservedEvent` o `StockRejectedEvent` espera confirmación del broker, pero sigue sin compartir una transacción distribuida con PostgreSQL; un fallo justo después del ACK del broker y antes del commit de la base todavía puede requerir reconciliación. Una outbox propia para `inventory-service` es la mejora siguiente para cerrar esa ventana.

---

## 2. Flujo de Transacciones Distribuidas (Saga)

### 2.1 Caso de Éxito (Happy Path)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Gateway as API Gateway
    participant Orders as Servicio Órdenes
    participant Kafka as Kafka Broker
    participant Inventory as Servicio Inventario
    participant Notifications as Servicio Notificaciones

    Cliente->>Gateway: POST /orders
    Gateway->>Orders: Enruta petición autenticada (JWT)
    Orders->>Orders: Guarda orden (Estado: PENDIENTE)
    Orders->>Kafka: Publica `OrderCreatedEvent` (tópico: order-events)
    Orders-->>Cliente: 201 Created (Order en estado PENDIENTE)

    Kafka->>Inventory: Consume `OrderCreatedEvent`
    Inventory->>Inventory: Reserva stock con Bloqueo Optimista (@Version)
    Inventory->>Kafka: Publica `StockReservedEvent` (tópico: inventory-events)

    Kafka->>Orders: Consume `StockReservedEvent`
    Orders->>Orders: Actualiza estado a CONFIRMADA
    Orders->>Kafka: Publica `OrderConfirmedEvent` (tópico: order-events)

    Kafka->>Notifications: Consume `OrderConfirmedEvent`
    Notifications->>Notifications: Simula envío de email/SMS al cliente
```

---

### 2.2 Caso de Fallo y Compensación (Stock Insuficiente)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Gateway as API Gateway
    participant Orders as Servicio Órdenes
    participant Kafka as Kafka Broker
    participant Inventory as Servicio Inventario
    participant Notifications as Servicio Notificaciones

    Cliente->>Gateway: POST /orders
    Gateway->>Orders: Enruta petición autenticada (JWT)
    Orders->>Orders: Guarda orden (Estado: PENDIENTE)
    Orders->>Kafka: Publica `OrderCreatedEvent` (tópico: order-events)
    Orders-->>Cliente: 201 Created (Order en estado PENDIENTE)

    Kafka->>Inventory: Consume `OrderCreatedEvent`
    Inventory->>Inventory: Valida stock disponible (Stock insuficiente)
    Inventory->>Kafka: Publica `StockRejectedEvent` (tópico: inventory-events)

    Kafka->>Orders: Consume `StockRejectedEvent`
    Orders->>Orders: Ejecuta compensación: Marca orden como CANCELADA
    Orders->>Kafka: Publica `OrderCancelledEvent` (tópico: order-events)

    Kafka->>Notifications: Consume `OrderCancelledEvent`
    Notifications->>Notifications: Simula notificación de cancelación por falta de stock
```

---

## 3. Estructura Estándar del Sobre (Event Envelope)

Todos los eventos publicados en Kafka comparten metadatos estándar de trazabilidad y tipado:

```json
{
  "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "eventType": "OrderCreatedEvent",
  "timestamp": "2026-09-25T17:50:00Z",
  "aggregateId": "order-1001",
  "payload": { ... }
}
```

---

## 4. Contratos de Eventos (Esquemas de Payload)

### 4.1 `OrderCreatedEvent`
* **Emitido por:** `orders-service` al crear un pedido en estado `PENDIENTE`.
* **Consumido por:** `inventory-service`.

```json
{
  "eventId": "b3e9b089-2bf3-4e4b-9e4a-4a6c8e3e4a01",
  "eventType": "OrderCreatedEvent",
  "timestamp": "2026-09-25T17:50:00Z",
  "aggregateId": "ord-88392",
  "payload": {
    "orderId": "ord-88392",
    "userId": "usr-1204",
    "items": [
      {
        "productId": "prod-101",
        "quantity": 2,
        "unitPrice": 49.99
      },
      {
        "productId": "prod-204",
        "quantity": 1,
        "unitPrice": 120.00
      }
    ],
    "totalAmount": 219.98
  }
}
```

---

### 4.2 `StockReservedEvent`
* **Emitido por:** `inventory-service` tras descontar exitosamente el stock.
* **Consumido por:** `orders-service`.

```json
{
  "eventId": "c71a39d4-631d-44a6-9efb-22db14f3c702",
  "eventType": "StockReservedEvent",
  "timestamp": "2026-09-25T17:50:01Z",
  "aggregateId": "ord-88392",
  "payload": {
    "orderId": "ord-88392",
    "reservationId": "res-9941",
    "items": [
      {
        "productId": "prod-101",
        "quantityReserved": 2
      },
      {
        "productId": "prod-204",
        "quantityReserved": 1
      }
    ]
  }
}
```

---

### 4.3 `StockRejectedEvent`
* **Emitido por:** `inventory-service` si al menos un producto no cuenta con existencias suficientes.
* **Consumido por:** `orders-service`.

```json
{
  "eventId": "e932b144-8849-43a1-9a7c-bb62d2948710",
  "eventType": "StockRejectedEvent",
  "timestamp": "2026-09-25T17:50:01Z",
  "aggregateId": "ord-88392",
  "payload": {
    "orderId": "ord-88392",
    "reason": "INSUFFICIENT_STOCK",
    "failedProducts": [
      {
        "productId": "prod-101",
        "requestedQuantity": 2,
        "availableQuantity": 1
      }
    ]
  }
}
```

---

### 4.4 `OrderConfirmedEvent`
* **Emitido por:** `orders-service` al confirmar la orden tras recibir `StockReservedEvent`.
* **Consumido por:** `notification-service`.

```json
{
  "eventId": "f1a2384a-34dc-4b71-b08e-161bca796031",
  "eventType": "OrderConfirmedEvent",
  "timestamp": "2026-09-25T17:50:02Z",
  "aggregateId": "ord-88392",
  "payload": {
    "orderId": "ord-88392",
    "userId": "usr-1204",
    "status": "CONFIRMADA",
    "totalAmount": 219.98
  }
}
```

---

### 4.5 `OrderCancelledEvent`
* **Emitido por:** `orders-service` al cancelar la orden por compensación tras recibir `StockRejectedEvent`.
* **Consumido por:** `notification-service`.

```json
{
  "eventId": "da8f7739-813c-41ad-b13c-333e6fa39211",
  "eventType": "OrderCancelledEvent",
  "timestamp": "2026-09-25T17:50:02Z",
  "aggregateId": "ord-88392",
  "payload": {
    "orderId": "ord-88392",
    "userId": "usr-1204",
    "status": "CANCELADA",
    "reason": "INSUFFICIENT_STOCK"
  }
}
```

---

## 5. Idempotencia y Resiliencia en el Consumidor

1. **Clave de Idempotencia:** Cada consumidor debe registrar el `eventId` procesado en su tabla local (o caché Redis con TTL) para evitar procesamiento duplicado ante reintentos de Kafka (*At-least-once delivery*).
2. **Desacoplamiento Estricto:** Cada servicio deserializa estos JSONs en sus propias clases internas (Consumer-Driven Contracts), garantizando que cambios no disruptivos no requieran redeploys simultáneos.
