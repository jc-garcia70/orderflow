package com.orderflow.users.domain.model;

import java.time.Instant;

public class User {

    private String id;
    private String email;
    private String password;
    private String fullName;
    private Role role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // Full Constructor
    public User(String id, String email, String password, String fullName, Role role,boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Constructor for when a user registers
    public User(String email, String password, String fullname, Role role){
        this(null,email,password,fullname,role,true, Instant.now(),Instant.now());
    }

    // -- Domain Business Logic --

    public void updateProfile(String fullName){
        this.fullName = fullName;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newHashedPassword){
        this.password = newHashedPassword;
        this.updatedAt = Instant.now();
    }

    public void changeRole(Role newRole){
        this.role = newRole;
        this.updatedAt = Instant.now();
    }

    public void activate(){
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate(){
        this.active = false;
        this.updatedAt = Instant.now();
    }




    // Getters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getEmail() {
        return email;
    }
    public String getPassword() {
        return password;
    }
    public String getFullName() {
        return fullName;
    }
    public Role getRole() {
        return role;
    }
    public boolean isActive() {
        return active;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
