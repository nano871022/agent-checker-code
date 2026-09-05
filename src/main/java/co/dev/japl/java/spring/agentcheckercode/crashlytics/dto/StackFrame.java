package co.dev.japl.java.spring.agentcheckercode.crashlytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StackFrame {
    private String packageName;
    private String className;
    private String fileName;
    private String methodName;
    private int lineNumber;
}
