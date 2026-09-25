# OrderFlow - Microservices Monorepo

Plataforma de procesamiento de órdenes y gestión de inventario basada en una arquitectura de microservicios con enfoque monorepo.

---

## Stack Tecnológico

| Componente | Tecnología / Versión |
| :--- | :--- |
| **Lenguaje** | Java 21 (LTS) |
| **Framework Base** | Spring Boot 4.1.x (Jakarta EE 11) |
| **Ecosistema Cloud** | Spring Cloud 2025.1.x |
| **Build Tool** | Apache Maven 3.9+ (Multi-módulo con Wrapper) |
| **Broker de Mensajería**| Apache Kafka (Modo KRaft) |
| **Caché / Sesiones** | Redis 7 |
| **Base de Datos** | PostgreSQL 16 (Patrón Database-per-Service) |
| **Trazabilidad** | OpenZipkin |
| **Service Discovery** | Netflix Eureka Server |



---

## Puesta en Marcha de la Infraestructura Local

### Prerrequisitos
- Docker Engine y Docker Compose instalados y en ejecución.
- JDK 21 configurado en variables de entorno (`JAVA_HOME`).

### 1. Variables de Entorno
Copia el archivo de plantilla `.env.example` a `.env` si deseas personalizar credenciales o puertos:
```bash
cp .env.example .env
```

### 2. Iniciar Servicios de Soporte
Levanta PostgreSQL, Kafka, Kafka-UI, Redis, Zipkin y Eureka con:
```bash
docker compose up -d
```

### 3. Puertos Expuestos

| Servicio | Puerto Host | Descripción / Dashboard |
| :--- | :--- | :--- |
| **PostgreSQL** | `5432` | Base de datos relacional multi-DB |
| **Kafka Broker** | `9092` | Broker de eventos PLAINTEXT |
| **Kafka UI** | `8085` | Consola web: `http://localhost:8085` |
| **Redis** | `6379` | Almacén en memoria |
| **Zipkin** | `9411` | UI de trazas distribuidas: `http://localhost:9411` |
| **Eureka Server** | `8761` | Registro de servicios: `http://localhost:8761` |

### 4. Detener Infraestructura
```bash
docker compose down
```

