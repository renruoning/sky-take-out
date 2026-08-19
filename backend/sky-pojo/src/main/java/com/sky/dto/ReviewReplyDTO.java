package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ReviewReplyDTO implements Serializable {

    private Long id;

    private String reply;
}
