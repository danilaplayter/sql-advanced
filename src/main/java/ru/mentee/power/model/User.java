package ru.mentee.power.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
  private Long id;
  private String email;
  private String firstName;
  private String lastName;
  private String phone;
  private String city;
  private LocalDateTime registrationDate;
  private Boolean isActive;
  private String status;
}