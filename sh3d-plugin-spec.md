> **АРХИВ / DEPRECATED**
>
> Этот документ описывает первоначальный дизайн плагина (TCP-архитектура).
> Актуальная архитектура — встроенный HTTP MCP-сервер (Streamable HTTP, JSON-RPC 2.0).
> См. [ARCHITECTURE.md](ARCHITECTURE.md) и [CLAUDE.md](CLAUDE.md) для текущей документации.

# Sweet Home 3D MCP Plugin — Концептуальное ТЗ

## Суть проекта

Плагин для Sweet Home 3D, который поднимает TCP-сервер внутри приложения и принимает JSON-команды для управления сценой (создание стен, размещение мебели, экспорт). Это серверная часть связки: внешний MCP-сервер подключается к этому плагину по TCP и транслирует команды от Claude.

## Архитектура

```
Внешний MCP-сервер (отдельный процесс) ──TCP socket :9877──► Этот плагин (внутри Sweet Home 3D JVM)
                                                                     │
                                                               Java API Sweet Home 3D
                                                                     │
                                                              Объекты: Home, Wall,
                                                              HomePieceOfFurniture, Room
```

Плагин живёт внутри процесса Sweet Home 3D и имеет прямой доступ к Java API приложения. Общение с внешним миром — через TCP-сокет на фиксированном порту (по умолчанию 9877, настраиваемый).

## Технологический стек

- **Java 11** (не 21 — Sweet Home 3D работает на Java 11+, плагин должен быть совместим с JVM приложения)
- **Без внешних зависимостей**, кроме того что уже есть в classpath Sweet Home 3D. Для JSON-парсинга использовать либо встроенные средства (Sweet Home 3D включает некоторые библиотеки), либо минимальный ручной парсер, либо вшить маленькую библиотеку (minimal-json, ~30KB) прямо в JAR
- **Сборка: Maven или Gradle** → результат: один `.sh3p`-файл (ZIP-архив, переименованный JAR)

## Формат плагина Sweet Home 3D

Sweet Home 3D загружает плагины из папки:
- Windows: `%APPDATA%\eTeks\Sweet Home 3D\plugins\`
- Альтернативно: `~/.eteks/sweethome3d/plugins/` (если домашняя директория не Windows-стандартная, путь зависит от конфигурации)

Плагин — это ZIP-архив с расширением `.sh3p` (может быть и `.jar`):

```
ApplicationPlugin.properties  → дескриптор плагина (id, name, class, version...)
META-INF/
  MANIFEST.MF                 → стандартный (без специальных атрибутов)
com/example/sh3dmcp/
  SH3DMcpPlugin.class         → extends com.eteks.sweethome3d.plugin.Plugin
  ...
```

SH3D обнаруживает плагин через `ApplicationPlugin.properties`, читая поле `class=`. Файл `MANIFEST.MF` не используется для обнаружения плагина.

Документация по плагинной системе:
- **Javadoc Plugin API**: http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/Plugin.html
- **PluginAction**: http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/PluginAction.html
- **Общий Javadoc**: http://www.sweethome3d.com/javadoc/

Ключевые классы для работы с моделью:
- `com.eteks.sweethome3d.model.Home` — корневой объект сцены
- `com.eteks.sweethome3d.model.Wall` — стена (задаётся двумя точками + толщина)
- `com.eteks.sweethome3d.model.Room` — комната (полигон пола)
- `com.eteks.sweethome3d.model.HomePieceOfFurniture` — размещённая мебель
- `com.eteks.sweethome3d.model.CatalogPieceOfFurniture` — мебель из каталога
- `com.eteks.sweethome3d.model.FurnitureCatalog` — каталог мебели
- `com.eteks.sweethome3d.plugin.PluginManager` — менеджер плагинов

## Протокол общения (TCP)

Текстовый, построчный: одна строка = один JSON-запрос, одна строка = один JSON-ответ.

### Формат запроса
```json
{"action": "create_walls", "params": {"x": 0, "y": 0, "width": 500, "height": 400, "thickness": 10}}
```

### Формат ответа
```json
{"status": "ok", "data": {"wallsCreated": 4, "message": "Room 500x400 created"}}
```
```json
{"status": "error", "message": "Furniture not found: xyz"}
```

## MVP-набор команд (Scope v0.1)

### 1. `create_walls`
Создаёт прямоугольную комнату из 4 стен.

Параметры:
- `x`, `y` — координаты верхнего левого угла (float, в сантиметрах — Sweet Home 3D работает в см)
- `width`, `height` — размеры (float, в см, например 500 = 5 метров)
- `thickness` — толщина стен (float, по умолчанию 10 см)

Логика:
- Создать 4 объекта `Wall` по периметру
- Соединить стены: `w1.setWallAtEnd(w2)`, `w2.setWallAtStart(w1)` и т.д. (важно для корректного рендеринга углов)
- Добавить в `Home` через `home.addWall()`

### 2. `place_furniture`
Размещает мебель из каталога Sweet Home 3D.

Параметры:
- `name` — поисковый запрос по имени мебели в каталоге (string, поиск через contains, case-insensitive)
- `x`, `y` — координаты размещения (float, в см)
- `angle` — угол поворота в градусах (float, по умолчанию 0)

Логика:
- Получить каталог через `getHome()` → поиск по категориям и наименованиям
- Создать `HomePieceOfFurniture` из найденного `CatalogPieceOfFurniture`
- Установить координаты и угол (угол в радианах: `Math.toRadians(angle)`)
- Добавить через `home.addPieceOfFurniture()`

### 3. `get_state`
Возвращает текущее состояние сцены.

Параметры: нет

Возвращает JSON:
- Количество стен
- Список мебели (имя, координаты x/y, угол, размеры)
- Количество комнат
- Размеры bounding box сцены (если возможно)

### 4. `list_furniture_catalog`
Возвращает список доступной мебели из каталога.

Параметры:
- `query` — поисковый запрос (string, опционально)
- `category` — фильтр по категории (string, опционально)

Возвращает: массив `{name, category, width, depth, height}` для каждого найденного элемента.

### 5. `ping`
Проверка связи. Возвращает `{"status": "ok", "version": "0.1.0"}`.

## Критические технические моменты

### EDT (Event Dispatch Thread)
Все модификации модели `Home` ДОЛЖНЫ выполняться в Swing EDT. TCP-сервер работает в отдельном потоке, поэтому все вызовы `home.addWall()`, `home.addPieceOfFurniture()` и т.д. нужно оборачивать в `SwingUtilities.invokeAndWait()` (не invokeLater — нужно дождаться выполнения, чтобы вернуть корректный ответ).

### Координатная система
Sweet Home 3D использует сантиметры. Ось X — вправо, ось Y — вниз (как в экранных координатах). Это важно документировать в описании инструментов MCP.

### Жизненный цикл сервера
- Плагин добавляет пункт меню (PluginAction) для старта/остановки TCP-сервера
- При старте — создаёт `ServerSocket` в отдельном daemon-потоке
- При остановке — закрывает сокет
- При закрытии Sweet Home 3D — сервер должен корректно остановиться

### Поиск мебели в каталоге
Каталог мебели доступен через `com.eteks.sweethome3d.model.FurnitureCatalog`. Он организован по категориям (`FurnitureCategory`), каждая содержит набор `CatalogPieceOfFurniture`. Поиск — итерация по всем категориям и элементам, проверка `getName().toLowerCase().contains(query)`.

## Сборка и деплой

Результат сборки — файл `.sh3p` (например `sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p`), который копируется в папку плагинов Sweet Home 3D или устанавливается двойным кликом. После перезапуска Sweet Home 3D плагин появляется в меню.

## Документация для изучения

- **Sweet Home 3D Javadoc (полный)**: http://www.sweethome3d.com/javadoc/
- **Исходный код Sweet Home 3D**: https://sourceforge.net/projects/sweethome3d/files/SweetHome3D-source/ (смотреть как устроены существующие плагины)
- **Примеры плагинов**: в исходниках Sweet Home 3D есть директория с примерами плагинов
- **Форум Sweet Home 3D**: https://sweethome3d.com/forum/ (вопросы по Plugin API)

## Что НЕ входит в MVP

- Создание комнат (Room) — только стены
- Экспорт в OBJ/glTF
- Управление текстурами и материалами
- Управление камерами и рендеринг
- Запись видеотуров
- Создание стен произвольной формы (только прямоугольные комнаты)
- Работа с несколькими этажами (levels)
