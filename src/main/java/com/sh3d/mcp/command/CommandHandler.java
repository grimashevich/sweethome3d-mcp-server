package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

/**
 * Интерфейс обработчика команды.
 * <p>
 * Метод вызывается в рабочем потоке (не в EDT).
 * Для взаимодействия с Home используйте accessor.runOnEDT().
 */
public interface CommandHandler {

    /**
     * Выполняет команду.
     *
     * @param request  распарсенный запрос (action + params)
     * @param accessor потокобезопасный мост к Sweet Home 3D
     * @return ответ (ok + data, либо error + message)
     */
    Response execute(Request request, HomeAccessor accessor);
}
