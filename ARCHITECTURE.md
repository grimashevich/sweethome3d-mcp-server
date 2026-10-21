# Sweet Home 3D MCP Plugin -- Архитектура

## Содержание

1. [Высокоуровневая архитектура](#1-высокоуровневая-архитектура)
2. [Структура пакетов и классов](#2-структура-пакетов-и-классов)
3. [HTTP MCP-сервер](#3-http-mcp-сервер)
4. [Система команд](#4-система-команд)
5. [Интеграция с Sweet Home 3D](#5-интеграция-с-sweet-home-3d)
6. [JSON-обработка](#6-json-обработка)
7. [Конфигурация](#7-конфигурация)
8. [Структура проекта (Maven)](#8-структура-проекта-maven)
9. [Диаграммы последовательности](#9-диаграммы-последовательности)
10. [Архитектурные решения (ADR)](#10-архитектурные-решения-adr)

---

Версия плагина: **1.2.0**. Поддерживаемая среда — Sweet Home 3D 7.x (проверено 7.5),
Java 8 или новее, включая встроенную Java 8. Сборка и тесты требуют JDK 11+ и Maven 3.9+.

## 1. Высокоуровневая архитектура

### 1.1 Контекстная диаграмма

```
+------------------+     HTTP (Streamable HTTP)      +----------------------------+
|                  |     JSON-RPC 2.0                |                            |
|  Claude (LLM)   | -------- POST /mcp -----------> |  SH3D MCP Plugin           |
|                  |     http://127.0.0.1:9877/mcp   |  (внутри Sweet Home 3D JVM)|
+------------------+                                 +----------------------------+
                                                              |
                                                      +-------v-----------+
                                                      |  Sweet Home 3D    |
                                                      |  Java API         |
                                                      |  (Home, Wall,     |
                                                      |   Furniture, etc.) |
                                                      +-------------------+
```

Плагин реализует MCP (Model Context Protocol) версии `2025-03-26` напрямую через Streamable HTTP.
Внешний MCP-сервер-прокси не требуется — Claude подключается к HTTP endpoint `/mcp` в плагине.

### 1.2 Диаграмма компонентов плагина

```
+============================================================================+
|                         SH3D MCP Plugin (JAR)                              |
|                                                                            |
|  +---------------------+     +------------------------------------------+  |
|  |   Plugin Lifecycle  |     |          HTTP MCP Server Layer            |  |
|  |                     |     |                                          |  |
|  |  SH3DMcpPlugin      |---->|  HttpMcpServer      McpRequestHandler   |  |
|  |  (extends Plugin)   |     |  (HttpServer)        (JSON-RPC 2.0)     |  |
|  |                     |     |                                          |  |
|  |  McpSettingsAction  |     |  SessionManager      JsonRpcProtocol    |  |
|  |  (PluginAction)     |     +-------------------|----------------------+  |
|  +---------------------+                         |                        |
|                                                  | dispatch(Request)      |
|                                                  v                        |
|  +----------------------------------------------------------------------+ |
|  |                      Command Layer                                   | |
|  |                                                                      | |
|  |  CommandRegistry -----> CommandHandler (interface)                    | |
|  |                         CommandDescriptor (auto-discovery)            | |
|  |                         46 handler-классов                            | |
|  +-------------------------------------|--------------------------------+ |
|                                        |                                  |
|                                        v                                  |
|  +----------------------------------------------------------------------+ |
|  |                      SH3D Bridge Layer                               | |
|  |                                                                      | |
|  |  HomeAccessor (thread-safe доступ к Home через EDT)                  | |
|  |  CheckpointManager (in-memory undo/redo через Home.clone())          | |
|  +----------------------------------------------------------------------+ |
+============================================================================+
```

### 1.3 Описание компонентов

| Компонент | Ответственность |
|-----------|----------------|
| **Plugin Lifecycle** | Инициализация/уничтожение плагина, создание пункта меню, управление жизненным циклом HTTP-сервера |
| **HTTP MCP Server Layer** | Приём HTTP-запросов, JSON-RPC 2.0 диспетчеризация, управление MCP-сессиями, DNS rebinding protection |
| **Command Layer** | Маршрутизация команд по имени, выполнение бизнес-логики, валидация параметров, auto-discovery |
| **SH3D Bridge Layer** | Безопасный доступ к объектам Sweet Home 3D с гарантией выполнения в EDT, checkpoint/restore |

### 1.4 Потоки данных

```
Claude (HTTP клиент)          Плагин
    |                          |
    |--- POST /mcp ---------->|  1. McpRequestHandler.handle()
    |   JSON-RPC 2.0          |  2. JsonRpcProtocol.parseRequest(body)
    |   method: tools/call     |  3. resolveAction(toolName) → action
    |                          |  4. CommandRegistry.dispatch(request, accessor)
    |                          |  5. handler.execute(request, homeAccessor)
    |                          |     5a. homeAccessor.runOnEDT(() -> ...)
    |                          |     5b. SwingUtilities.invokeAndWait(...)
    |                          |  6. JsonRpcProtocol.formatToolCallResult(response)
    |<--- JSON-RPC result ----|  7. sendJson(200, result)
    |                          |
```

---

## 2. Структура пакетов и классов

### 2.1 Иерархия пакетов

```text
com.sh3d.mcp/
├── plugin/                         # Точка входа и интерфейс настроек
│   ├── SH3DMcpPlugin.java          # Жизненный цикл, общий HTTP-сервер, реестр команд
│   ├── McpSettingsAction.java      # Пункт меню Tools → MCP Server...
│   └── McpSettingsDialog.java      # Статус, порт, токен, настройка клиента
├── http/                           # Streamable HTTP и JSON-RPC 2.0
│   ├── HttpMcpServer.java          # Сервер на 127.0.0.1, /mcp и /health
│   ├── RequestGuard.java           # Фильтр Host/Origin, токена и размера запроса
│   ├── McpRequestHandler.java      # Диспетчеризация MCP-запросов
│   ├── JsonRpcProtocol.java        # Парсинг и форматирование JSON-RPC
│   ├── McpSession.java             # Состояние сессии
│   └── SessionManager.java         # Хранение сессий и TTL
├── server/
│   ├── ServerState.java            # Состояния жизненного цикла
│   └── ServerStateListener.java    # Подписка на смену состояния
├── protocol/
│   ├── JsonUtil.java               # JSON без внешних зависимостей
│   ├── Request.java                # action + params, учёт прочитанных параметров
│   └── Response.java               # status + data/message
├── command/
│   ├── CommandHandler.java         # Интерфейс обработчика
│   ├── CommandDescriptor.java      # Описание и JSON Schema для tools/list
│   ├── CommandRegistry.java        # Реестр action → handler
│   ├── handler/                    # 46 обработчиков команд
│   │   ├── GetStateHandler.java
│   │   ├── GenerateShapeHandler.java
│   │   ├── ModifyLabelHandler.java
│   │   ├── DeleteLabelHandler.java
│   │   └── ...
│   ├── shape/                      # Генераторы примитивов, extrude и mesh
│   │   ├── ShapeGenerator.java
│   │   ├── ShapeGeneratorSupport.java
│   │   ├── ShapeCommonParams.java
│   │   ├── BoxShapeGenerator.java  # И другие *ShapeGenerator
│   │   └── csg/                    # Булевы операции над геометрией
│   │       ├── CsgNode.java
│   │       ├── CsgOperation.java
│   │       ├── CsgPlane.java
│   │       ├── CsgPolygon.java
│   │       ├── CsgVertex.java
│   │       ├── CsgShapeGenerator.java
│   │       └── CsgTimeoutException.java
│   └── util/                       # Общая валидация, форматирование и геометрия
│       ├── ValidationUtil.java
│       ├── FormatUtil.java
│       ├── SchemaBuilder.java
│       ├── SchemaUtil.java
│       ├── ColorParser.java
│       ├── CatalogSearchUtil.java
│       ├── CatalogAliases.java
│       ├── HomeFurnitureFactory.java
│       ├── LabelStyleUtil.java
│       ├── SashUtil.java
│       ├── OverheadCameraComputer.java
│       ├── SceneBounds.java
│       └── SceneBoundsCalculator.java
├── bridge/                         # Доступ к Sweet Home 3D API
│   ├── HomeAccessor.java           # Синхронный доступ к модели через EDT
│   ├── CheckpointManager.java      # Снимки, стабильные номера, курсор истории
│   ├── ObjectResolver.java         # Поиск объектов по стабильному строковому ID
│   ├── CommandException.java       # Ошибки выполнения задач в EDT
│   ├── PathValidator.java          # Пути и расширения файлов
│   ├── HomeCleaner.java            # Очистка сцены
│   └── SafeHomeReader.java         # Проверка и чтение Home.xml без десериализации
└── config/
    ├── PluginConfig.java           # Настройки и версия плагина
    ├── AuthTokenStore.java         # Создание, хранение и ротация токена
    └── ClaudeDesktopConfigurator.java # Конфигурация локального stdio-моста
```

### 2.2 Описание ключевых классов

#### Пакет `plugin`

**`SH3DMcpPlugin extends com.eteks.sweethome3d.plugin.Plugin`**
- Главный класс плагина, указывается в `ApplicationPlugin.properties`
- `getActions()` -- создаёт `HomeAccessor`, `CommandRegistry` (46 команд), `HttpMcpServer`
- При `autoStart=true` запускает HTTP-сервер сразу
- `destroy()` -- переключает сервер на оставшийся Home; при закрытии последнего Home останавливает сервер

**`McpSettingsAction extends PluginAction`**
- Пункт меню "MCP Server..." (в меню Tools)
- `execute()` -- открывает `McpSettingsDialog` (модальный Swing-диалог настроек)
- Диалог показывает статус сервера, порт, позволяет start/stop и автоконфигурацию Claude Desktop

**`McpSettingsDialog extends JDialog`**
- Swing-диалог настроек MCP-сервера
- Показывает текущий статус (RUNNING/STOPPED), порт, autoStart и режим токена
- Кнопки Start/Stop для управления сервером
- Интеграция с `ClaudeDesktopConfigurator` для автоконфигурации Claude Desktop

#### Пакет `http`

**`HttpMcpServer`**
- Управляет `com.sun.net.httpserver.HttpServer` на `127.0.0.1:port`
- `ThreadPoolExecutor` (core=4, max=16, `SynchronousQueue`) с daemon-потоками для обработки запросов
- Жизненный цикл: STOPPED → STARTING → RUNNING → STOPPING → STOPPED
- Endpoints: `/mcp` (MCP) и `/health` (проверка состояния)

**`McpRequestHandler implements HttpHandler`**
- Обрабатывает POST (JSON-RPC 2.0), GET (SSE -- не реализовано), DELETE (cleanup)
- Запросы проходят через `RequestGuard` до обработчика
- `tools/list` -- собирает tools из `CommandDescriptor` через `CommandRegistry`
- `tools/call` -- диспетчеризация через `CommandRegistry.dispatch()`

**`JsonRpcProtocol`**
- Статические методы для парсинга JSON-RPC 2.0 запросов и форматирования ответов
- Коды ошибок: `PARSE_ERROR(-32700)`, `INVALID_REQUEST(-32600)`, `METHOD_NOT_FOUND(-32601)`, `INVALID_PARAMS(-32602)`, `INTERNAL_ERROR(-32603)`
- Трансформация `Response` (из CommandHandler) → MCP `tools/call` result с поддержкой image content (base64)

**`SessionManager`**
- `ConcurrentHashMap` сессий, TTL 30 минут, lazy cleanup
- `createSession()` / `getSession()` / `removeSession()`

**`McpSession`**
- Value object: sessionId (UUID), protocolVersion, timestamp, initialized (boolean)

#### Пакет `server`

**`ServerState (enum)`**
```
STOPPED, STARTING, RUNNING, STOPPING
```

**`ServerStateListener (@FunctionalInterface)`**
```java
void onStateChanged(ServerState oldState, ServerState newState);
```

#### Пакет `protocol`

**`JsonUtil`**
- Низкоуровневый JSON-парсер без внешних библиотек
- `parse(String) → Object` -- recursive descent (String, Number, Boolean, null, Map, List)
- `serialize(Object) → String` -- рекурсивная сериализация в JSON-строку
- `appendValue(StringBuilder, Object)` / `appendString(StringBuilder, String)` -- для построения JSON

**`Request`** (value object)
- `action: String`, `params: Map<String, Object>`
- `getAction()` возвращает имя команды, `getParams()` — представление параметров с учётом обращений к ключам
- `getString(key)` возвращает строковое представление значения или `null`; `getRequiredString(key)` требует наличия значения, но не проверяет пустую строку
- `getFloat(key)` требует параметр, `getFloat(key, defaultValue)` использует значение по умолчанию при отсутствии
- `getBoolean(key)` возвращает `Boolean` или `null`, а для значения другого типа бросает `IllegalArgumentException`
- `getUnrecognizedKeys()` возвращает переданные, но не прочитанные ключи; `markAccessed(key)` отмечает ключ прочитанным явно
- Проверки диапазона, конечности чисел и непустых строк выполняют обработчики

**`Response`** (value object)
- Статические фабрики: `Response.ok(data)`, `Response.error(message)`
- `isOk()`, `getData()`, `getMessage()`

#### Пакет `bridge`

**`HomeAccessor`**
- Обёртка над `Home` и `UserPreferences`
- `runOnEDT(Callable<T>)` -- `SwingUtilities.invokeAndWait()` для синхронного выполнения в EDT
- Read-only доступ: `getHome()`, `getUserPreferences()`, `getFurnitureCatalog()`

**`CheckpointManager`**
- In-memory undo/redo через `Home.clone()`
- Таймлайн с курсором, fork при новом checkpoint после restore
- Снимок неизменен: при восстановлении в сцену уходит его копия (`LoadHomeHandler.replaceScene()` — общий код `restore_checkpoint` и `load_home`), поэтому правки сцены после восстановления снимок не затрагивают
- `maxDepth=32`; при нехватке памяти старые снимки удаляются
- Номера снимков растут в течение жизни менеджера и не переиспользуются после удаления, ветвления или очистки истории; `cursor` и `restoredTo` используют эти номера

**`ObjectResolver`**
- Статический utility-класс для поиска объектов Home по стабильному строковому ID
- Использует `HomeObject.getId()` (SH3D 7.x) — ID автогенерируется, стабилен при удалении других объектов, сохраняется при сериализации и клонировании
- Методы: `findWall()`, `findFurniture()`, `findRoom()`, `findLevel()`, `findDimensionLine()`, `findLabel()`, `findPolyline()`, `findSelectable()`
- Используется handler-классами для операций modify/delete/connect по ID объекта

**`CommandException`**
- Unchecked exception (`RuntimeException`) для ошибок, возникающих при выполнении задач в EDT
- Бросается из `HomeAccessor.runOnEDT()` при ошибках в callable
- Перехватывается на уровне `McpRequestHandler` и трансформируется в JSON-RPC error response

**`PathValidator`**
- Валидация и нормализация файловых путей
- Нормализация путей (устранение `..` через `Path.normalize()`)
- Проверка допустимых расширений файлов
- Используется handler'ами для операций с файловой системой (save, load, export, render)

---

## 3. HTTP MCP-сервер

### 3.1 Модель потоков (Threading Model)

```
+------------------------------------------------------------------+
|                        JVM Sweet Home 3D                         |
|                                                                  |
|  +----------------+                                              |
|  | EDT            | <--- SwingUtilities.invokeAndWait() ----+    |
|  | (Swing thread) |     (мутации Home, чтение состояния)    |    |
|  +----------------+                                         |    |
|                                                             |    |
|  +------------------+                                       |    |
|  | HttpServer       |  ThreadPoolExecutor (daemon)           |    |
|  | (127.0.0.1:9877) |  core=4, max=16, SynchronousQueue     |    |
|  +--------|---------+                                       |    |
|           |                                                 |    |
|           |  HTTP request                                   |    |
|           v                                                 |    |
|  +------------------+                                       |    |
|  | pool-thread-N    |  daemon=true                          |    |
|  | (McpRequestHandler)  parse -> dispatch -> execute -------+    |
|  +------------------+   <- format <- respond                     |
|                                                                  |
+------------------------------------------------------------------+
```

**Потоки:**

| Поток | Тип | Ответственность |
|-------|-----|----------------|
| **EDT** (Swing) | Основной UI-поток SH3D | Все мутации модели Home, чтение состояния |
| **pool-thread-N** | daemon (ThreadPoolExecutor) | Обработка HTTP-запросов, JSON-RPC диспетчеризация |

`HttpServer` использует `ThreadPoolExecutor` с `SynchronousQueue` и `DaemonThreadFactory`.
Параметры пула: core=4, max=16 потоков, keepAlive=60 секунд. `SynchronousQueue` обеспечивает
прямую передачу задач потокам без буферизации, что предотвращает накопление задач в очереди.

### 3.2 Жизненный цикл сервера

```
                  HttpMcpServer
                     |
  start() ---------> |
                     |  state = STARTING
                     |  new ThreadPoolExecutor(4, 16, 60s, SynchronousQueue, daemon)
                     |  HttpServer.create(127.0.0.1:port, 0)
                     |  createContext("/mcp", McpRequestHandler)
                     |  HttpServer.start()
                     |  state = RUNNING
                     |
                     |  ... обработка HTTP-запросов ...
                     |
  stop() ----------> |
                     |  state = STOPPING
                     |  HttpServer.stop(1)     // 1 сек на завершение активных запросов
                     |  executor.shutdown()
                     |  executor.awaitTermination(5s)
                     |  state = STOPPED
```

Триггеры остановки:
- Пользователь нажал "Stop" в диалоге настроек (McpSettingsDialog)
- Вызван `Plugin.destroy()` при закрытии последнего Home
- JVM завершается (daemon-потоки умирают автоматически)

### 3.3 Протокол: MCP Streamable HTTP

#### Транспортный уровень
- **Транспорт:** HTTP (com.sun.net.httpserver.HttpServer, встроен в JDK)
- **Bind:** `127.0.0.1:9877` (только localhost)
- **Endpoint:** `/mcp`
- **Протокол:** JSON-RPC 2.0 поверх HTTP (MCP Streamable HTTP spec `2025-03-26`)

#### HTTP-методы

| Метод | Назначение | Статус |
|-------|-----------|--------|
| **POST** | JSON-RPC 2.0 запросы (initialize, tools/list, tools/call, ping) | Реализовано |
| **GET** | SSE-поток для server→client уведомлений | 405 (не реализовано) |
| **DELETE** | Завершение MCP-сессии | Реализовано |

#### MCP-методы (POST /mcp)

| Метод | Описание | Ответ |
|-------|---------|-------|
| `initialize` | Handshake, создание сессии | `Mcp-Session-Id` header + capabilities |
| `notifications/initialized` | Подтверждение от клиента | 202 Accepted |
| `tools/list` | Список доступных tools (из CommandDescriptor) | tools array |
| `tools/call` | Вызов tool → dispatch через CommandRegistry | content (text/image) |
| `ping` | Проверка жизнеспособности | пустой result |

#### Формат запроса (JSON-RPC 2.0)
```json
{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "get_state", "arguments": {}}}
```

#### Формат ответа (успех)
```json
{"jsonrpc": "2.0", "id": 1, "result": {"content": [{"type": "text", "text": "{...}"}], "isError": false}}
```

#### Формат ответа (ошибка)
```json
{"jsonrpc": "2.0", "id": 1, "error": {"code": -32601, "message": "Unknown tool: xyz"}}
```

#### Управление сессиями
- `initialize` создаёт сессию, возвращает `Mcp-Session-Id` в HTTP header
- `tools/list` и `tools/call` требуют валидный `Mcp-Session-Id` header
- `DELETE /mcp` удаляет сессию
- TTL сессий: 30 минут, lazy cleanup

#### Безопасность HTTP

`RequestGuard` — фильтр перед `/mcp` и `/health`. Сервер слушает только `127.0.0.1`;
`Host` и переданный `Origin` всегда проверяются на loopback-адрес и порт сервера,
повторы заголовков и списки значений отклоняются с `403`. CORS-заголовки не отправляются.
Лимит тела запроса — 5 МБ (`413` при превышении).

Токен по умолчанию выключен. Галочка **Require a token** в **Tools → MCP Server...**
включает проверку `Authorization: Bearer <token>` для `/mcp` со следующего запроса,
в том числе в существующих сессиях; `/health` токена не требует.
`AuthTokenStore` хранит токен и файл заголовка в папке плагинов с правами только владельца.
При включённой проверке отсутствие или неверное значение токена даёт `401`,
а невозможность получить обязательный токен не позволяет запустить сервер.
Без токена любая локальная программа может управлять сценой и записывать файлы с правами SH3D.

`load_home` использует `SafeHomeReader`: приватная временная копия, ровно один `Home.xml`,
запрет DOCTYPE, XXE и внешнего содержимого, лимит распакованного XML 64 МБ.
Java-десериализация записи `Home` не выполняется. Старые файлы без `Home.xml`
нужно открыть и пересохранить в Sweet Home 3D.

### 3.4 Graceful Shutdown

Остановка выполняется в следующем порядке:

1. Установить `state = STOPPING` (через CAS)
2. `HttpServer.stop(1)` -- ждёт до 1 секунды для завершения активных запросов
3. `executor.shutdown()` + `awaitTermination(5 секунд)`
4. Если не завершились -- `executor.shutdownNow()`
5. Установить `state = STOPPED`

---

## 4. Система команд

### 4.1 Паттерн: Command + Registry + Descriptor

Применяется паттерн **Command** в комбинации с **Registry** и **Descriptor** (auto-discovery).
Каждая команда инкапсулирована в отдельном классе. `CommandRegistry` связывает имя
с обработчиком. `CommandDescriptor` предоставляет описание и JSON Schema для MCP `tools/list`.

```
CommandRegistry (46 команд)
  |
  |-- "get_state"               --> GetStateHandler
  |-- "create_walls"            --> CreateWallsHandler
  |-- "place_furniture"         --> PlaceFurnitureHandler
  |-- "render_photo"            --> RenderPhotoHandler
  |-- "batch_commands"          --> BatchCommandsHandler
  |-- "generate_shape"          --> GenerateShapeHandler
  |-- "duplicate_objects"       --> DuplicateObjectsHandler
  |-- "group_furniture"         --> GroupFurnitureHandler
  |-- ... (остальные обработчики)
```

### 4.2 Интерфейсы

```java
public interface CommandHandler {
    Response execute(Request request, HomeAccessor accessor);
}

public interface CommandDescriptor {
    String getDescription();              // Описание для Claude (английский)
    Map<String, Object> getSchema();      // JSON Schema параметров
    default String getToolName() { return null; }  // MCP tool name (если отличается от action)
}
```

Большинство handler-классов реализуют оба интерфейса.

### 4.3 Как добавить новую команду

Добавление новой команды требует **ровно двух шагов**:

**Шаг 1.** Создать `command/handler/MyCommandHandler.java`, реализующий `CommandHandler` + `CommandDescriptor`:

```java
package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.LinkedHashMap;
import java.util.Map;

public class MyCommandHandler implements CommandHandler, CommandDescriptor {
    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Missing or empty parameter: name");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("wallCount", accessor.getHome().getWalls().size());
            return result;
        });
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Returns a name and the current wall count.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("name", "Name to include in the result (non-empty)")
                .build();
    }
}
```

**Шаг 2.** Зарегистрировать в `SH3DMcpPlugin.createCommandRegistry()`:

```java
registry.register("my_command", new MyCommandHandler());
```

Других изменений не требуется: HTTP-сервер, JSON-RPC, MCP `tools/list` --
всё работает автоматически через `CommandDescriptor`.

### 4.4 Обработка ошибок и валидация

Каждый `CommandHandler` отвечает за валидацию своих параметров. При ошибке
обработчик возвращает `Response.error(...)`. Исключения перехватываются на
уровне `McpRequestHandler`.

**Трёхуровневая обработка ошибок:**

```
Уровень 1: HTTP/JSON-RPC Layer (McpRequestHandler + JsonRpcProtocol)
   - Невалидный JSON (PARSE_ERROR -32700)
   - Отсутствие поля "method" (INVALID_REQUEST -32600)
   - Неизвестный MCP-метод (METHOD_NOT_FOUND -32601)

Уровень 2: Command Registry (CommandRegistry.dispatch)
   - Неизвестный tool (METHOD_NOT_FOUND -32601)

Уровень 3: Command Handler (CommandHandler.execute)
   - Невалидные параметры (тип, диапазон, обязательность)
   - Бизнес-ошибки (мебель не найдена, и т.д.)
   - Ошибки EDT (InterruptedException, CommandException)
```

Ошибки уровней 1–2 (протокол, неизвестный инструмент) возвращаются в верхнем поле JSON-RPC `error`.
Ошибки уровня 3 — это `Response.error(...)` (в него же `CommandRegistry` превращает исключения обработчика): клиент
получает JSON-RPC `result` с `isError: true` и текстом ошибки в `content`, как требует MCP для ошибок выполнения
инструмента.

---

## 5. Интеграция с Sweet Home 3D

### 5.1 Plugin API

`SH3DMcpPlugin.getActions()` загружает настройки и создаёт для каждого Home собственные
`HomeAccessor` и реестр 46 команд. Первый экземпляр создаёт общий `HttpMcpServer`
и запускает его при `autoStart=true`; открытие следующего Home переключает контекст
общего сервера через `switchContext(registry, accessor)`. `destroy()` переключает
контекст на последний оставшийся Home либо останавливает сервер, если закрыт последний Home.

**Метаданные плагина** (`ApplicationPlugin.properties` в корне JAR):

```properties
id=Plugin#SH3DMcp
name=SH3D MCP Plugin
class=com.sh3d.mcp.plugin.SH3DMcpPlugin
description=HTTP MCP server for Model Context Protocol integration
version=1.2.0
license=GPL v2
provider=SH3D MCP Project
applicationMinimumVersion=6.0
javaMinimumVersion=1.8
```

### 5.1.1 Локализация (i18n)

UI плагина локализован на 18 языков через стандартный Java `ResourceBundle`. MCP-команды и их описания остаются на английском (Claude их читает).

**Resource Bundles:**

| Bundle | Расположение | Ключей | Назначение |
|--------|-------------|--------|-----------|
| `McpPlugin` | `com/sh3d/mcp/plugin/McpPlugin[_XX].properties` | — | Все UI-строки диалога и экшена |
| `ApplicationPlugin` | `ApplicationPlugin[_XX].properties` | 2 | name + description для Plugin Manager |
| `McpSettingsAction` | `com/sh3d/mcp/plugin/McpSettingsAction[_XX].properties` | 1 | Name для SH3D PluginAction framework |

**Языки:** en (base), fr, de, es, it, ru, zh_CN, zh_TW, ja, pt, pt_BR, nl, sv, cs, pl, hu, el, bg, vi

**Загрузка:** `McpSettingsAction` загружает `McpPlugin` bundle через `ResourceBundle.getBundle("com.sh3d.mcp.plugin.McpPlugin", Locale.getDefault())` и передаёт в `McpSettingsDialog` через конструктор. Для динамических строк используется `java.text.MessageFormat`.

### 5.2 EDT -- безопасная модификация модели

Все объекты модели Sweet Home 3D (Home, Wall, HomePieceOfFurniture) привязаны к EDT.
Любая мутация извне EDT приведет к непредсказуемому поведению UI.

**HomeAccessor.runOnEDT()** -- центральный метод для безопасного доступа:

```java
public <T> T runOnEDT(Callable<T> task) {
    if (SwingUtilities.isEventDispatchThread()) {
        return task.call();  // Уже в EDT
    }

    AtomicReference<T> resultRef = new AtomicReference<>();
    AtomicReference<Exception> errorRef = new AtomicReference<>();

    SwingUtilities.invokeAndWait(() -> {
        try {
            resultRef.set(task.call());
        } catch (Exception e) {
            errorRef.set(e);
        }
    });

    if (errorRef.get() != null) {
        throw new CommandException(...);
    }
    return resultRef.get();
}
```

**Почему `invokeAndWait`, а не `invokeLater`:**
- Нужен синхронный ответ: HTTP-клиент ждёт результат
- Нужно вернуть данные из EDT
- Нужно поймать исключения и передать их обратно в HTTP-поток

### 5.3 Доступ к объектам Sweet Home 3D

| Что нужно | Как получить | Где используется |
|-----------|-------------|-----------------|
| `Home` | `Plugin.getHome()` → `HomeAccessor` | Все command handlers |
| `FurnitureCatalog` | `Plugin.getUserPreferences().getFurnitureCatalog()` | PlaceFurniture, ListCatalog |
| `TexturesCatalog` | `Plugin.getUserPreferences().getTexturesCatalog()` | ApplyTexture, ListTextures |
| `ExportableView` | `Plugin.getHomeController().getPlanController().getView()` | ExportSvg, ExportPlanImage |
| `HomeController` | `Plugin.getHomeController()` | resolvePlanView() |

---

## 6. JSON-обработка

### 6.1 Двухуровневая архитектура

JSON-обработка разделена на два уровня:

| Уровень | Класс | Назначение |
|---------|-------|-----------|
| **Низкоуровневый** | `JsonUtil` (пакет `protocol`) | Recursive descent парсер/сериализатор. Работает с примитивами: String, Number, Boolean, null, Map, List |
| **MCP-уровень** | `JsonRpcProtocol` (пакет `http`) | JSON-RPC 2.0 форматирование. Использует `JsonUtil` для низкоуровневых операций |

### 6.2 Выбор: встроенный ручной парсер

**Решение:** Минимальный ручной JSON-парсер/сериализатор в `JsonUtil`.
Не используется Gson, Jackson, minimal-json или другие внешние библиотеки.

**Обоснование:**
- Нулевые внешние зависимости, нулевой риск classpath-конфликтов
- HTTP-сервер (`com.sun.net.httpserver`) встроен в JDK — не нарушает zero-deps
- Наш JSON предсказуем: JSON-RPC 2.0 envelope + плоские параметры команд

### 6.3 JsonUtil API

```
Парсинг:
  parse(String json) → Object  (Map, List, String, Number, Boolean, null)

Сериализация:
  serialize(Object) → String
  appendValue(StringBuilder, Object)  — рекурсивная сериализация
  appendString(StringBuilder, String) — экранирование строк
```

### 6.4 JsonRpcProtocol API

```
Парсинг:
  parseRequest(String json) → Map<String, Object>
  getMethod(request) → String
  getId(request) → Object
  getParams(request) → Map<String, Object>

Форматирование:
  formatResult(id, result) → String                  // JSON-RPC success
  formatError(id, code, message) → String            // JSON-RPC error
  formatInitializeResult(id, protocolVersion) → String
  formatToolsListResult(id, tools) → String
  formatToolCallResult(id, Response) → String        // Response → MCP content
```

---

## 7. Конфигурация

### 7.1 Настраиваемые параметры

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `port` | int | 9877 | HTTP-порт сервера |
| `autoStart` | boolean | true | Автоматический запуск сервера при загрузке плагина |
| `logLevel` | String | INFO | Уровень логирования (FINE, INFO, WARNING, SEVERE) |
| `requireToken` | boolean | false | Требовать bearer-токен на `/mcp` |

### 7.2 Хранение настроек

Настройки хранятся в файле `sh3d-mcp.properties` в папке плагинов пользователя:
- Windows: `%APPDATA%\eTeks\Sweet Home 3D\plugins\sh3d-mcp.properties`
- macOS: `~/Library/Application Support/eTeks/Sweet Home 3D/plugins/sh3d-mcp.properties`
- Linux: `~/.eteks/sweethome3d/plugins/sh3d-mcp.properties`

**Формат файла:**
```properties
# SH3D MCP Plugin Configuration
sh3d.mcp.port=9877
sh3d.mcp.autoStart=true
sh3d.mcp.logLevel=INFO
sh3d.mcp.requireToken=false
```

**Поиск конфигурации (приоритет):**

1. Системные свойства Java: `-Dsh3d.mcp.port=9878` (наивысший приоритет)
2. Файл `sh3d-mcp.properties` в папке плагинов
3. Значения по умолчанию (зашиты в `PluginConfig`)

### 7.3 Автоконфигурация Claude Desktop

`ClaudeDesktopConfigurator` — утилита для автоматической интеграции с Claude Desktop:
- Мержит секцию `mcpServers.sweethome3d` в `claude_desktop_config.json`
- Кросс-платформенный поиск конфига (Windows: `%APPDATA%\Claude\`, macOS: `~/Library/Application Support/Claude/`, Linux: `~/.config/Claude/`)
- Создаёт `.bak` бэкап перед модификацией существующего конфига
- Claude Desktop не загружает `"type": "http"` — используется `npx -y mcp-remote@0.14.3` как stdio-мост к `http://127.0.0.1:port/mcp`
- При включённом токене добавляет `--header-file` с путём к `sh3d-mcp.token.headers`, без значения токена в конфигурации клиента
- Вызывается из `McpSettingsDialog` по нажатию кнопки

### 7.4 Логирование

Лог записывается в файл `sh3d-mcp.log` в папке плагинов (rotated, 2 файла по 1 МБ).
Уровень настраивается через `logLevel`.

---

## 8. Структура проекта (Maven)

### 8.1 Структура директорий

```
sh3d-mcp-plugin/
|
|-- pom.xml
|-- lib/
|   |-- SweetHome3D.jar          # SH3D API (system scope)
|   |-- j3dcore.jar              # Java 3D (system scope)
|   |-- j3dutils.jar
|   |-- vecmath.jar
|
|-- src/
|   |-- main/
|   |   |-- java/com/sh3d/mcp/
|   |   |   |-- plugin/          # SH3DMcpPlugin, McpSettingsAction, McpSettingsDialog
|   |   |   |-- http/            # HttpMcpServer, RequestGuard, McpRequestHandler, ...
|   |   |   |-- server/          # ServerState, ServerStateListener
|   |   |   |-- protocol/        # JsonUtil, Request, Response
|   |   |   |-- command/         # Интерфейсы и реестр
|   |   |   |   |-- handler/     # 46 обработчиков
|   |   |   |   |-- shape/       # Генераторы геометрии
|   |   |   |   |   |-- csg/     # Булевы операции
|   |   |   |   |-- util/        # Общие утилиты
|   |   |   |-- bridge/          # HomeAccessor, CheckpointManager, ObjectResolver, SafeHomeReader, ...
|   |   |   |-- config/          # PluginConfig, AuthTokenStore, ClaudeDesktopConfigurator
|   |   |
|   |   |-- resources/
|   |       |-- ApplicationPlugin.properties
|   |       |-- com/sh3d/mcp/plugin/
|   |           |-- McpSettingsAction.properties
|   |
|   |-- test/
|       |-- java/com/sh3d/mcp/   # Более 3000 тестов
|
|-- ARCHITECTURE.md
|-- sh3d-plugin-spec.md
```

### 8.2 Maven POM (ключевые фрагменты)

```xml
<groupId>com.sh3d.mcp</groupId>
<artifactId>sh3d-mcp-plugin</artifactId>
<version>1.2.0</version>
<packaging>jar</packaging>

<properties>
    <maven.compiler.release>8</maven.compiler.release>
    <maven.compiler.testRelease>11</maven.compiler.testRelease>
</properties>

<dependencies>
    <!-- SH3D + Java3D (system scope, уже в classpath при запуске) -->
    <!-- JUnit 5 + Mockito 5.21 (test scope) -->
</dependencies>
```

**Примечание:** SH3D не публикуется в Maven Central. JAR берётся из установки SH3D
и лежит в `lib/SweetHome3D.jar` с `<scope>system</scope>`.

### 8.3 Сборка и деплой

```bash
# Сборка (JDK 11+, Maven 3.9+ через wrapper)
./mvnw clean package

# Результат
target/sh3d-mcp-plugin-1.2.0.sh3p

# Деплой (Windows)
copy target\sh3d-mcp-plugin-1.2.0.sh3p "%APPDATA%\eTeks\Sweet Home 3D\plugins\"

# Деплой (bash / Git Bash)
cp target/sh3d-mcp-plugin-1.2.0.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"
```

---

## 9. Диаграммы последовательности

### 9.1 MCP Handshake: initialize → tools/list

```
Claude                    McpRequestHandler     SessionManager    CommandRegistry
  |                            |                     |                |
  |-- POST /mcp ------------->|                     |                |
  |   method: initialize       |                     |                |
  |                            |-- createSession --->|                |
  |                            |<- McpSession -------|                |
  |                            |                     |                |
  |<-- 200 + Mcp-Session-Id --|                     |                |
  |   result: {capabilities,   |                     |                |
  |    serverInfo, version}    |                     |                |
  |                            |                     |                |
  |-- POST /mcp ------------->|                     |                |
  |   method: tools/list       |                     |                |
  |   Mcp-Session-Id: ...      |                     |                |
  |                            |-- validateSession ->|                |
  |                            |<- OK ---------------|                |
  |                            |                     |                |
  |                            |-- getHandlers() ----|--------------->|
  |                            |<- Map<action, handler> -------------|
  |                            |                     |                |
  |                            |   for each CommandDescriptor:        |
  |                            |     collect name, description, schema|
  |                            |                     |                |
  |<-- 200 -------------------|                     |                |
  |   result: {tools: [...]}   |                     |                |
  |                            |                     |                |
```

### 9.2 Успешный tools/call: get_state

```
Claude                    McpRequestHandler    CommandRegistry    GetStateHandler    HomeAccessor    EDT
  |                            |                    |                  |                 |            |
  |-- POST /mcp ------------->|                    |                  |                 |            |
  |   method: tools/call       |                    |                  |                 |            |
  |   params: {name:           |                    |                  |                 |            |
  |    "get_state"}            |                    |                  |                 |            |
  |                            |                    |                  |                 |            |
  |                            |-- dispatch ------->|                  |                 |            |
  |                            |                    |-- execute ------>|                 |            |
  |                            |                    |                  |                 |            |
  |                            |                    |                  |-- runOnEDT ---->|            |
  |                            |                    |                  |                 |-- invoke ->|
  |                            |                    |                  |                 |            |
  |                            |                    |                  |                 |   collect  |
  |                            |                    |                  |                 |   walls,   |
  |                            |                    |                  |                 |   furniture|
  |                            |                    |                  |                 |   rooms... |
  |                            |                    |                  |                 |            |
  |                            |                    |                  |                 |<- data ----|
  |                            |                    |                  |<- result -------|            |
  |                            |                    |<- Response.ok ---|                 |            |
  |                            |<- Response --------|                  |                 |            |
  |                            |                    |                  |                 |            |
  |                            |  formatToolCallResult(response)      |                 |            |
  |                            |  → content: [{type: "text", text: JSON}]               |            |
  |                            |                    |                  |                 |            |
  |<-- 200 JSON-RPC result ---|                    |                  |                 |            |
  |                            |                    |                  |                 |            |
```

### 9.3 Сценарий с ошибкой: неизвестный tool

```
Claude                    McpRequestHandler
  |                            |
  |-- POST /mcp ------------->|
  |   method: tools/call       |
  |   params: {name: "xyz"}    |
  |                            |
  |                            |  resolveAction("xyz") → null
  |                            |
  |<-- 200 -------------------|
  |   error: {code: -32601,    |
  |    message: "Unknown tool: |
  |    xyz"}                   |
  |                            |
```

### 9.4 Сценарий: невалидный JSON

```
Claude                    McpRequestHandler    JsonRpcProtocol
  |                            |                    |
  |-- POST /mcp ------------->|                    |
  |   body: "not json"        |                    |
  |                            |-- parseRequest --->|
  |                            |   IllegalArgument  |
  |                            |                    |
  |<-- 400 -------------------|                    |
  |   error: {code: -32700,    |                    |
  |    message: "Invalid JSON: |                    |
  |    ..."}                   |                    |
  |                            |                    |
```

---

## 10. Архитектурные решения (ADR)

### ADR-001: Построчный текстовый протокол поверх TCP

**Статус:** Superseded by ADR-009

Исходное решение: TCP с построчным JSON. Было оправдано для MVP (простота, отладка через telnet,
ноль зависимостей). Заменено на HTTP MCP после перехода на встроенный MCP-сервер в плагине.

---

### ADR-002: Command Pattern с Registry для обработки команд

**Статус:** Принято (действует)

Паттерн Command + Registry хорошо масштабируется. С 5 команд в MVP вырос до 46 команд,
добавление новой команды по-прежнему = 1 класс + 1 строка регистрации. Расширен интерфейсом
`CommandDescriptor` для auto-discovery (MCP `tools/list`).

---

### ADR-003: SwingUtilities.invokeAndWait для доступа к модели

**Статус:** Принято (действует)

Паттерн не изменился при переходе с TCP на HTTP. HTTP-потоки (pool threads) используют
`HomeAccessor.runOnEDT()` так же, как раньше TCP-потоки.

---

### ADR-004: Ручной JSON-парсер вместо внешней библиотеки

**Статус:** Принято (действует)

Ручной парсер (`JsonUtil`) покрыт проверками крайних случаев; в проекте более 3000 тестов.
Добавлен JSON-RPC 2.0 слой (`JsonRpcProtocol`) поверх `JsonUtil`.

---

### ADR-005: Один поток на клиентское соединение (Thread-per-Connection)

**Статус:** Superseded by ADR-009

Thread-per-connection заменён на `ThreadPoolExecutor` с `SynchronousQueue`, управляемый `HttpServer`.
Потоки создаются по мере необходимости (core=4, max=16) и переиспользуются.

---

### ADR-006: Пакет com.sh3d.mcp как корневой namespace

**Статус:** Обновлено

Корневой пакет: `com.sh3d.mcp` (изменён с `com.eteks.sweethome3d.mcp`). Более короткий
namespace, отделяющий плагин от кодовой базы SH3D.

---

### ADR-007: ApplicationPlugin.properties как единственный механизм обнаружения

**Статус:** Принято (действует)

Без изменений. `PluginManager` SH3D по-прежнему находит плагин через
`ApplicationPlugin.properties` в ZIP-архиве (.sh3p).

---

### ADR-008: Конфигурация через properties-файл и System properties

**Статус:** Принято (действует)

Расширен: добавлен параметр `logLevel`, `autoStart` default изменён на `true`,
удалён `maxLineLength` (неактуален для HTTP).

---

### ADR-009: Встроенный HTTP MCP-сервер вместо TCP + внешнего прокси

**Статус:** Принято

**Контекст:**
Исходная архитектура (ADR-001) предполагала TCP-сервер в плагине + внешний MCP-сервер (Java 21,
MCP SDK) как прокси между Claude и плагином. Это работало, но имело недостатки:
два отдельных процесса, две сборки, необходимость синхронизации версий.

**Решение:** Встроить MCP-сервер (Streamable HTTP) непосредственно в плагин.

**Реализация:**
- HTTP-сервер: `com.sun.net.httpserver.HttpServer` (встроен в JDK, ноль зависимостей)
- Протокол: MCP Streamable HTTP (JSON-RPC 2.0) версии `2025-03-26`
- Threading: `ThreadPoolExecutor` (core=4, max=16, `SynchronousQueue`) с daemon-потоками
- Auto-discovery: `CommandDescriptor` → `tools/list` (было: `describe_commands` → внешний MCP-сервер)

**Последствия:**
- (+) Один процесс, одна сборка, одна конфигурация
- (+) По-прежнему ноль внешних зависимостей (`com.sun.net.httpserver` в JDK)
- (+) Прямое подключение Claude → плагин (меньше latency)
- (+) `.mcp.json` стал проще: `"type": "http"` вместо stdio + JAR path
- (-) Встроенный `HttpServer` не поддерживает HTTP/2 и асинхронный API
- (-) Плагин берёт на себя JSON-RPC 2.0 и MCP session management (компенсируется простотой протокола)

---

## Приложение A: Координатная система

```
     X axis (вправо) -->
   0 +----------------------------->
     |
     |   (x, y) - верхний левый угол
     |   +------- width -------+
     |   |                     |
     | h |                     |
     | e |      Комната        |
     | i |                     |
     | g |                     |
     | h |                     |
     | t +---------------------+
     |
     v
   Y axis (вниз)

   Единицы: сантиметры (500 = 5 метров)
```

## Приложение B: Пример сессии (curl)

Пример для режима без токена (по умолчанию). При включённом токене добавьте
`-H "Authorization: Bearer <token>"` к каждому запросу на `/mcp`.

```bash
# 1. Initialize (-D - печатает заголовки ответа, среди них Mcp-Session-Id)
curl -sS -D - -X POST http://127.0.0.1:9877/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
# → {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"sweethome3d","version":"1.2.0"}}}
# Запомнить Mcp-Session-Id из response header

# 2. Tools list
curl -s -X POST http://127.0.0.1:9877/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
# → {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_state","description":"...","inputSchema":{...}}, ...]}}

# 3. Call tool: get_state
curl -s -X POST http://127.0.0.1:9877/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_state","arguments":{}}}'
# → {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"wallCount\":0,...}"}],"isError":false}}

# 4. Ping
curl -s -X POST http://127.0.0.1:9877/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"ping"}'
# → {"jsonrpc":"2.0","id":4,"result":{}}
```
