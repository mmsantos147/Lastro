package io.matheusms.lastro.adapter.in.mcp;

import static io.matheusms.lastro.tools.ToolDefinitions.ALLOCATION_VS_TARGET;
import static io.matheusms.lastro.tools.ToolDefinitions.ALLOCATION_VS_TARGET_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.AMOUNT_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.ANALYSIS_AMOUNT_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.ASSET_CLASS_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.DISTRIBUTION_HISTORY;
import static io.matheusms.lastro.tools.ToolDefinitions.DISTRIBUTION_HISTORY_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.LIST_POSITIONS;
import static io.matheusms.lastro.tools.ToolDefinitions.LIST_POSITIONS_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.MONTHS_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.PORTFOLIO_SUMMARY;
import static io.matheusms.lastro.tools.ToolDefinitions.PORTFOLIO_SUMMARY_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.SAVE_ANALYSIS;
import static io.matheusms.lastro.tools.ToolDefinitions.SAVE_ANALYSIS_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.SUGGESTION_ID_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.SUGGEST_CONTRIBUTION;
import static io.matheusms.lastro.tools.ToolDefinitions.SUGGEST_CONTRIBUTION_DESCRIPTION;
import static io.matheusms.lastro.tools.ToolDefinitions.TEXT_PARAM;
import static io.matheusms.lastro.tools.ToolDefinitions.TITLE_PARAM;

import io.matheusms.lastro.application.NotFoundException;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.service.MissingMarketDataException;
import io.matheusms.lastro.tools.PortfolioTools;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Binds the portfolio tools to MCP. Results are JSON with snake_case names (the same style as the tool and
 * parameter names). Parameter names are snake_case on purpose: they are the public names Claude sees.
 */
@Component
class PortfolioMcpTools {

    private static final Logger log = LoggerFactory.getLogger(PortfolioMcpTools.class);

    private final PortfolioTools tools;
    private final McpUser user;
    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    PortfolioMcpTools(PortfolioTools tools, McpUser user) {
        this.tools = tools;
        this.user = user;
    }

    @McpTool(name = PORTFOLIO_SUMMARY, description = PORTFOLIO_SUMMARY_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    CallToolResult portfolioSummary() {
        return run(() -> tools.portfolioSummary(user.id()));
    }

    @McpTool(name = LIST_POSITIONS, description = LIST_POSITIONS_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    CallToolResult listPositions(@McpToolParam(description = ASSET_CLASS_PARAM, required = false) String asset_class) {
        return run(() -> tools.listPositions(user.id(), asset_class == null || asset_class.isBlank()
                ? null : AssetClass.valueOf(asset_class.strip().toUpperCase())));
    }

    @McpTool(name = ALLOCATION_VS_TARGET, description = ALLOCATION_VS_TARGET_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    CallToolResult allocationVsTarget() {
        return run(() -> tools.allocationVsTarget(user.id()));
    }

    /** Not read-only: every run is persisted, so an analysis can link to it. */
    @McpTool(name = SUGGEST_CONTRIBUTION, description = SUGGEST_CONTRIBUTION_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false))
    CallToolResult suggestContribution(@McpToolParam(description = AMOUNT_PARAM, required = true) BigDecimal amount) {
        return run(() -> tools.suggestContribution(user.id(), amount));
    }

    @McpTool(name = DISTRIBUTION_HISTORY, description = DISTRIBUTION_HISTORY_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    CallToolResult distributionHistory(@McpToolParam(description = MONTHS_PARAM, required = false) Integer months) {
        return run(() -> tools.distributionHistory(user.id(), months));
    }

    @McpTool(name = SAVE_ANALYSIS, description = SAVE_ANALYSIS_DESCRIPTION,
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false))
    CallToolResult saveAnalysis(
            @McpToolParam(description = TITLE_PARAM, required = true) String title,
            @McpToolParam(description = TEXT_PARAM, required = true) String text,
            @McpToolParam(description = ANALYSIS_AMOUNT_PARAM, required = false) BigDecimal amount,
            @McpToolParam(description = SUGGESTION_ID_PARAM, required = false) Long suggestion_id) {
        return run(() -> tools.saveAnalysis(user.id(), title, text, amount, suggestion_id));
    }

    /** Expected failures become tool errors Claude can read and explain; anything else is logged. */
    private CallToolResult run(Supplier<Object> tool) {
        try {
            return CallToolResult.builder().addTextContent(json.writeValueAsString(tool.get())).isError(false).build();
        } catch (IllegalArgumentException | NotFoundException | MissingMarketDataException exception) {
            return error(exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("MCP tool failed", exception);
            return error("Internal error while running the tool");
        }
    }

    private CallToolResult error(String message) {
        return CallToolResult.builder()
                .addTextContent(json.writeValueAsString(Map.of("error", message)))
                .isError(true)
                .build();
    }
}
