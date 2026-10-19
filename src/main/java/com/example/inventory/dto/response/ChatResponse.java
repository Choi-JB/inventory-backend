
package com.example.inventory.dto.response;

import java.util.List;
import com.example.inventory.tool.SourceCollector;

/**
 * ChatResponse - 채팅 응답
 */
public record ChatResponse(
    String answer,
    List<SourceCollector.Source> sources
) {}
