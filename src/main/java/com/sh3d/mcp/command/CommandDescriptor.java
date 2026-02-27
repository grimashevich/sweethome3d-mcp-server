package com.sh3d.mcp.command;

import java.util.Map;

/**
 * Интерфейс описания команды для auto-discovery.
 * Handler, реализующий этот интерфейс, будет автоматически
 * зарегистрирован как MCP tool на стороне MCP-сервера.
 *
 * Инфраструктурные команды (ping, describe_commands) не реализуют
 * этот интерфейс и не видны через auto-discovery.
 */
public interface CommandDescriptor {

    /**
     * MCP tool name. Если null — используется action name из registry.
     * Позволяет registry-имя (create_walls) отличаться от MCP-имени (create_room).
     */
    default String getToolName() {
        return null;
    }

    /**
     * Описание для Claude (на английском, 1-3 предложения).
     */
    String getDescription();

    /**
     * JSON Schema параметров как Map (сериализуется через JsonUtil).
     * Должен содержать "type": "object", "properties": {...}.
     */
    Map<String, Object> getSchema();
}
