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
|  |                         42 handler-класса                             | |
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

```
com.sh3d.mcp/
|
|-- plugin/                         # Точка входа плагина и SH3D-интеграция
|   |-- SH3DMcpPlugin.java         # extends Plugin, точка входа
|   |-- McpSettingsAction.java      # PluginAction — открывает McpSettingsDialog (настройки MCP-сервера)
|   |-- McpSettingsDialog.java     # Swing-диалог настроек: статус, порт, autoStart, Claude Desktop конфиг
|
|-- http/                           # HTTP MCP-сервер (Streamable HTTP)
|   |-- HttpMcpServer.java         # com.sun.net.httpserver.HttpServer, lifecycle
|   |-- McpRequestHandler.java     # HttpHandler для /mcp endpoint
|   |-- JsonRpcProtocol.java       # JSON-RPC 2.0 парсинг/форматирование
|   |-- McpSession.java            # Value object MCP-сессии
|   |-- SessionManager.java        # Управление сессиями (ConcurrentHashMap, TTL)
|
|-- server/                         # Состояние сервера (переиспользуется из HTTP)
|   |-- ServerState.java           # Enum: STOPPED, STARTING, RUNNING, STOPPING
|   |-- ServerStateListener.java   # Functional interface для подписки на смену состояния
|
|-- protocol/                       # JSON-утилиты и value objects
|   |-- JsonUtil.java              # Recursive descent JSON-парсер/сериализатор
|   |-- Request.java               # Value object: action + params
|   |-- Response.java              # Value object: status + data/message
|
|-- command/                        # Обработчики команд (42 handler-класса + утилиты)
|   |-- CommandHandler.java        # Интерфейс обработчика
|   |-- CommandDescriptor.java     # Интерфейс auto-discovery (description + schema)
|   |-- CommandRegistry.java       # Реестр action -> handler
|   |-- ValidationUtil.java        # Общие валидации параметров (извлечено из handler'ов)
|   |-- FormatUtil.java            # Форматирование данных (buildFurnitureInfo и др.)
|   |-- SchemaUtil.java            # Утилиты для построения JSON Schema
|   |-- ColorParser.java           # Парсинг цветов (hex, именованные)
|   |-- CatalogSearchUtil.java     # Поиск по каталогу мебели
|   |-- CatalogAliases.java        # Алиасы для каталога мебели
|   |-- OverheadCameraComputer.java # Вычисление позиции камеры сверху
|   |-- SceneBounds.java           # Value object границ сцены
|   |-- SceneBoundsCalculator.java # Вычисление границ сцены
|   |-- BatchCommandsHandler.java
|   |-- CreateWallsHandler.java
|   |-- PlaceFurnitureHandler.java
|   |-- GetStateHandler.java
|   |-- RenderPhotoHandler.java
|   |-- GenerateShapeHandler.java  # Произвольные 3D-фигуры (делегирует в *ShapeGenerator)
|   |-- DuplicateObjectsHandler.java # Дублирование объектов
|   |-- GroupFurnitureHandler.java # Группировка мебели
|   |-- UngroupFurnitureHandler.java # Разгруппировка мебели
|   |-- ... (ещё ~30 handler-классов)
|   |-- *ShapeGenerator.java       # Генераторы форм: Arch, Box, Cone, Cylinder, Extrude,
|   |                              #   Hemisphere, Mesh, Pipe, Sphere, Stairs, Torus, Wedge
|   |-- ShapeGeneratorSupport.java # Базовый класс для генераторов форм
|   |-- Csg*.java                  # CSG-операции: CsgNode, CsgOperation, CsgPlane, CsgPolygon, CsgVertex
|
|-- bridge/                         # Мост к Sweet Home 3D API
|   |-- HomeAccessor.java          # Thread-safe обертка над Home через EDT
|   |-- CheckpointManager.java     # In-memory undo/redo (Home.clone())
|   |-- ObjectResolver.java        # Поиск объектов Home по стабильному строковому ID (HomeObject.getId())
|   |-- CommandException.java      # Unchecked exception для ошибок EDT
|   |-- PathValidator.java         # Валидация и нормализация файловых путей (path traversal protection)
|
|-- config/                         # Конфигурация
    |-- PluginConfig.java          # Настраиваемые параметры
    |-- ClaudeDesktopConfigurator.java  # Автоконфигурация Claude Desktop (мерж MCP-секции в claude_desktop_config.json)
```

### 2.2 Описание ключевых классов

#### Пакет `plugin`

**`SH3DMcpPlugin extends com.eteks.sweethome3d.plugin.Plugin`**
- Главный класс плагина, указывается в `ApplicationPlugin.properties`
- `getActions()` -- создаёт `HomeAccessor`, `CommandRegistry` (42 команды), `HttpMcpServer`
- При `autoStart=true` запускает HTTP-сервер сразу
- `destroy()` -- останавливает HTTP-сервер при закрытии Home

**`McpSettingsAction extends PluginAction`**
- Пункт меню "MCP Server..." (в меню Tools)
- `execute()` -- открывает `McpSettingsDialog` (модальный Swing-диалог настроек)
- Диалог показывает статус сервера, порт, позволяет start/stop и автоконфигурацию Claude Desktop

**`McpSettingsDialog extends JDialog`**
- Swing-диалог настроек MCP-сервера
- Показывает текущий статус (RUNNING/STOPPED), порт, autoStart
- Кнопки Start/Stop для управления сервером
- Интеграция с `ClaudeDesktopConfigurator` для автоконфигурации Claude Desktop

#### Пакет `http`

**`HttpMcpServer`**
- Управляет `com.sun.net.httpserver.HttpServer` на `127.0.0.1:port`
- `ThreadPoolExecutor` (core=4, max=16, `SynchronousQueue`) с daemon-потоками для обработки запросов
- Жизненный цикл: STOPPED → STARTING → RUNNING → STOPPING → STOPPED
- Один endpoint: `/mcp`

**`McpRequestHandler implements HttpHandler`**
- Обрабатывает POST (JSON-RPC 2.0), GET (SSE -- не реализовано), DELETE (cleanup)
- DNS rebinding protection через валидацию Origin заголовка
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
- Методы: `getString(key)`, `getFloat(key)`, `getFloat(key, default)`, `getInt(key, default)`, `getBoolean(key, default)`

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
- `maxDepth=32`, ~50-200 KB на снимок

**`ObjectResolver`**
- Статический utility-класс для поиска объектов Home по стабильному строковому ID
- Использует `HomeObject.getId()` (SH3D 7.x) — ID автогенерируется, стабилен при удалении других объектов, сохраняется при сериализации и клонировании
- Методы: `findWall()`, `findFurniture()`, `findRoom()`, `findLevel()`, `findDimensionLine()`
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
- Вызван `Plugin.destroy()` (закрытие Home)
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

#### Безопасность
- Привязка к `127.0.0.1` (только localhost)
- DNS rebinding protection: валидация `Origin` header (допускаются только localhost origins)

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
CommandRegistry (42 команды)
  |
  |-- "get_state"               --> GetStateHandler
  |-- "create_walls"            --> CreateWallsHandler
  |-- "place_furniture"         --> PlaceFurnitureHandler
  |-- "render_photo"            --> RenderPhotoHandler
  |-- "batch_commands"          --> BatchCommandsHandler
  |-- "generate_shape"          --> GenerateShapeHandler
  |-- "duplicate_objects"       --> DuplicateObjectsHandler
  |-- "group_furniture"         --> GroupFurnitureHandler
  |-- ... (ещё 34 обработчика)
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

**Шаг 1.** Создать класс, реализующий `CommandHandler` + `CommandDescriptor`:

```java
public class MyNewHandler implements CommandHandler, CommandDescriptor {
    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String param = request.getString("myParam");
        if (param == null) {
            return Response.error("Missing required parameter: myParam");
        }

        Object result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            // ... работа с моделью ...
            return someResult;
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", result);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "English description for Claude";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        // ... properties
        schema.put("properties", props);
        schema.put("required", Arrays.asList("myParam"));
        return schema;
    }
}
```

**Шаг 2.** Зарегистрировать в `SH3DMcpPlugin.createCommandRegistry()`:

```java
registry.register("my_new_command", new MyNewHandler());
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

Каждый уровень возвращает корректный JSON-RPC error response.

---

## 5. Интеграция с Sweet Home 3D

### 5.1 Plugin API

```java
public class SH3DMcpPlugin extends Plugin {
    private HttpMcpServer httpServer;

    @Override
    public PluginAction[] getActions() {
        HomeAccessor accessor = new HomeAccessor(getHome(), getUserPreferences());
        ExportableView planView = resolvePlanView();
        CommandRegistry registry = createCommandRegistry(planView);
        httpServer = new HttpMcpServer(PluginConfig.load(), registry, accessor);

        if (config.isAutoStart()) {
            httpServer.start();
        }

        return new PluginAction[] {
            new McpSettingsAction(this, httpServer)
        };
    }

    @Override
    public void destroy() {
        if (httpServer != null && httpServer.isRunning()) {
            httpServer.stop();
        }
    }

    private CommandRegistry createCommandRegistry(ExportableView planView) {
        CommandRegistry registry = new CommandRegistry();
        // 42 команды: checkpoint, create_walls, place_furniture, get_state, list_categories, ...
        return registry;
    }
}
```

**Метаданные плагина** (`ApplicationPlugin.properties` в корне JAR):

```properties
id=Plugin#SH3DMcp
name=SH3D MCP Plugin
class=com.sh3d.mcp.plugin.SH3DMcpPlugin
description=HTTP MCP server for Model Context Protocol integration
version=0.1.0
license=MIT
provider=SH3D MCP Project
applicationMinimumVersion=6.0
javaMinimumVersion=11
```

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

### 7.2 Хранение настроек

Настройки хранятся в файле `sh3d-mcp.properties` в папке плагинов пользователя:
- Windows: `%APPDATA%\eTeks\Sweet Home 3D\plugins\sh3d-mcp.properties`
- macOS: `~/Library/Application Support/eTeks/Sweet Home 3D/plugins/sh3d-mcp.properties`
- Linux: `~/.eteks/sweethome3d/plugins/sh3d-mcp.properties`

**Формат файла:**
```properties
# SH3D MCP Plugin Configuration
port=9877
autoStart=true
logLevel=INFO
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
- Claude Desktop не поддерживает `"type": "http"` — используется `npx mcp-remote` как stdio-мост к HTTP endpoint плагина
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
|   |   |   |-- http/            # HttpMcpServer, McpRequestHandler, JsonRpcProtocol, ...
|   |   |   |-- server/          # ServerState, ServerStateListener
|   |   |   |-- protocol/        # JsonUtil, Request, Response
|   |   |   |-- command/         # CommandHandler, CommandRegistry, 42 handlers, утилиты, shape generators
|   |   |   |-- bridge/          # HomeAccessor, CheckpointManager, CommandException, PathValidator
|   |   |   |-- config/          # PluginConfig
|   |   |
|   |   |-- resources/
|   |       |-- ApplicationPlugin.properties
|   |       |-- com/sh3d/mcp/plugin/
|   |           |-- McpSettingsAction.properties
|   |
|   |-- test/
|       |-- java/com/sh3d/mcp/   # ~89 тестовых классов
|
|-- ARCHITECTURE.md
|-- CLAUDE.md
|-- PRD.md
|-- TO-DOS.md
|-- sh3d-plugin-spec.md
```

### 8.2 Maven POM (ключевые фрагменты)

```xml
<groupId>com.sh3d.mcp</groupId>
<artifactId>sh3d-mcp-plugin</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>jar</packaging>

<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
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
# Сборка
mvn clean package

# Результат
target/sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p

# Деплой (Windows)
copy target\sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p "%APPDATA%\eTeks\Sweet Home 3D\plugins\"

# Деплой (bash / Git Bash)
cp target/sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"
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

Паттерн Command + Registry хорошо масштабируется. С 5 команд в MVP вырос до 42 команды,
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

Ручной парсер (`JsonUtil`) доказал надёжность: ~700 тестов проходят, edge-cases покрыты.
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
- (-) Java 11 ограничивает возможности HTTP-сервера (нет HTTP/2, нет async)
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

```bash
# 1. Initialize
curl -s -X POST http://localhost:9877/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
# → {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"sweethome3d","version":"0.1.0"}}}
# Запомнить Mcp-Session-Id из response header

# 2. Tools list
curl -s -X POST http://localhost:9877/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
# → {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"get_state","description":"...","inputSchema":{...}}, ...]}}

# 3. Call tool: get_state
curl -s -X POST http://localhost:9877/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_state","arguments":{}}}'
# → {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"wallCount\":0,...}"}],"isError":false}}

# 4. Ping
curl -s -X POST http://localhost:9877/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"ping"}'
# → {"jsonrpc":"2.0","id":4,"result":{}}
```
