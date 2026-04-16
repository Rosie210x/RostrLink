package com.rostrlink.api;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JsonResponse<T> {
    private String status;
    private String message;
    private T data;
    private String errorCode;
    private Object errorData;
}
