package com.ping.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * User
 * @author humdeef
 * @date 2026/4/7
 * @desc User
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO implements Serializable {
    private Integer id;
    private String  traNo;
}
