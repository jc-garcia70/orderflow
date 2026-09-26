package com.orderflow.users.application.port.in;

/**
 * Inbound port for authenticated user password change.
 */
public interface ChangePasswordUseCase {

    record ChangePasswordCommand(
            String userId,
            String currentPassword,
            String newPassword
    ){}

    void changePassword(ChangePasswordCommand command);


}
