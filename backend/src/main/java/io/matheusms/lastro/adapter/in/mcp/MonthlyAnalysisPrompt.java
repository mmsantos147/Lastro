package io.matheusms.lastro.adapter.in.mcp;

import static io.matheusms.lastro.tools.ToolDefinitions.MONTHLY_ANALYSIS_PROMPT;
import static io.matheusms.lastro.tools.ToolDefinitions.MONTHLY_ANALYSIS_PROMPT_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.PROMPT_AMOUNT_PARAM;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The {@code monthly_analysis} prompt. Its text is the classpath resource {@link #RESOURCE}.
 */
@Component
class MonthlyAnalysisPrompt {

    static final String RESOURCE = "prompts/prompt-analise-carteira.md";

    private final String instructions;

    MonthlyAnalysisPrompt() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            this.instructions = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Analysis prompt not found on the classpath: " + RESOURCE, exception);
        }
    }

    @McpPrompt(name = MONTHLY_ANALYSIS_PROMPT, description = MONTHLY_ANALYSIS_PROMPT_DESCRIPTION)
    GetPromptResult monthlyAnalysis(@McpArg(name = "amount", description = PROMPT_AMOUNT_PARAM, required = false) String amount) {
        var request = amount == null || amount.isBlank()
                ? "Faça a análise mensal da minha carteira. Pergunte o valor do aporte se precisar."
                : "Faça a análise mensal da minha carteira para um aporte de R$ %s.".formatted(amount.strip());
        var text = instructions + "\n\n---\n\n" + request;
        return new GetPromptResult(MONTHLY_ANALYSIS_PROMPT_DESCRIPTION,
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }
}
