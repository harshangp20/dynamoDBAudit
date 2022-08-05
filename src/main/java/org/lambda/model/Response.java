package org.lambda.model;

import lombok.*;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class Response {

    private String result;
    private String failReason;
    private List<String > offenders;
    private Boolean scoredControl;
    private String Description;
    private String controlId;
    private String processDescription;
    private String severity;
    private String category;

}
