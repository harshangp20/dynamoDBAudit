package org.lambda.model;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
@Setter
public class Request {

    public String scanId;
    private String accountId;
    private String region;

}
