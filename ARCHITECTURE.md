# Архитектура Weather Forecast

## Основное решение

Приложение использует прагматичный MVVM с однонаправленным потоком данных (UDF), offline-first repository и Room как источником истины для сохранённых городов и погоды.

По подтверждённому заданию WF-023 все продуктовые слои выделены из `:app` в физические Gradle-модули: чистые JVM `:core:model`, `:core:network`, `:domain`; Android libraries `:core:database`, `:core:ui`, `:data`, `:worker`, `:feature:home`, `:feature:forecast`, `:feature:settings`. В `:app` остаются Application, Activity, корневая тема, Navigation Compose и сборка Koin-графа. Это composition root, а не ещё один слой данных или экрана.

```text
:app ──→ :feature:home / :feature:forecast / :feature:settings
  │                          └──→ :domain / :core:model / :core:ui
  ├──→ :data ────────────────────→ :domain / :core:model / :core:network / :core:database
  ├──→ :worker ──────────────────→ :domain
  └──→ :core:network / :core:database / :core:ui / :domain / :core:model

:domain ──→ :core:model
:core:ui ─→ :core:model
```

Стрелки означают compile-time зависимости. `:core:database` содержит Room, `:core:ui` — общую тему и погодные ресурсы. `:core:model`, `:core:network` и `:domain` — JVM-модули без Android-плагина. Ни один вынесенный модуль не зависит от `:app`; межмодульные ресурсы и публичные API проверены сборкой и тестами.

## Направление зависимостей

```text
Compose Screen
    ↑ immutable UiState       ↓ UI callbacks
Stateful Route / ViewModel
    ↑ Flow/StateFlow          ↓ domain repository calls
domain/repository contracts
    ↑ domain models           ↓ operations
data repository implementations
    ↙                                      ↘
Room / DAO (source of truth)        Retrofit / Open-Meteo
```

Основные правила:

- UI не создаёт Retrofit, Room, DAO, SharedPreferences или repository.
- ViewModel зависит от domain contracts, а не от data implementations.
- Network DTO, Room entities, domain models и UI models — разные типы с явными mapper-функциями.
- Успешная сеть сначала преобразуется и сохраняется в Room; UI получает новый результат из Room Flow.
- Ошибка сети не очищает последний успешный кэш.
- `MainActivity` только включает edge-to-edge и размещает корневую тему с NavHost.

## Пакеты и ответственность

| Пакет | Ответственность | Не должен знать о |
|---|---|---|
| `core/model` | Чистые модели, °C/°F, WMO mapping, clock contract | Android, Room, Retrofit, Compose, Koin |
| `core/network` | Retrofit API, Open-Meteo contract и nullable DTO | UI, Room entities |
| `core/database` | Room entities, DAO, database, migration | Compose, Retrofit DTO |
| `data/mapper` | DTO→domain и entity↔domain/cache mapping | Compose state |
| `data` | Remote/local orchestration, одноразовое чтение разрешённого местоположения и SharedPreferences implementation | NavController, Composable |
| `domain/repository` | Минимальные contracts и типизированные refresh results | Android implementation details |
| `feature/home` | Поиск, избранные, stale refresh, Home state/UI | Retrofit и DAO |
| `feature/forecast` | Forecast state machine, presentation selection, три вкладки | Retrofit и DAO |
| `feature/settings` | Настройки и согласованная очистка | SharedPreferences/DAO напрямую |
| `ui/theme` | Одна Material 3 тема и system-bar appearance | Data layer |
| `ui/weather` | Общие русские погодные ресурсы и форматирование | Network DTO/Room |
| `navigation` | Три destination и стабильный `cityId` | Полный объект города |
| `di` | Сборка Koin definitions | Бизнес-логика |
| `worker` | WorkManager adapter и расписание | Global service locator |

## UDF и состояния экранов

State идёт вниз, события идут вверх:

```text
Repository Flow → ViewModel → immutable UiState → Screen
Repository/API  ← ViewModel ← callback/action      ← пользователь
```

Stateful `Route` получает ViewModel через Koin, lifecycle-aware собирает `StateFlow` через `collectAsStateWithLifecycle` и обрабатывает одноразовые effects. Stateless `Screen` получает только state и callbacks, поэтому его можно проверять без настоящей сети и базы.

### Главная

`HomeViewModel` объединяет четыре источника:

- `FavoriteCitiesRepository.observeCities()`;
- `SettingsRepository.settings`;
- `DeviceLocationRepository.latestCoordinates` с последним согласованным названием текущего города;
- внутреннее interaction state поиска/добавления/обновления.

Экран этой зоны называется «Города». При включённом автоматическом режиме он вставляет карточку текущего местоположения перед секцией избранного. Callback карточки удаляет из back stack только маршрут списка и возвращает к уже существующему `weather`, поэтому погодная Главная не дублируется.

Поиск имеет состояния `Idle`, `Loading`, `Results`, `Empty`, `Error`. Строки короче двух символов не отправляются, debounce равен 400 мс, а generation + cancellation не позволяют старому ответу заменить новый. Snackbar/navigation-like события передаются отдельным `Channel`, поэтому не повторяются после recomposition.

При `ON_RESUME` проверяются timestamps городов. Только записи без кэша или старше 60 минут обновляются; существующая карточка остаётся видимой вместе с компактным прогрессом.

### Прогноз

Для избранного прогноза NavHost передаёт только `Long cityId`. `ForecastViewModel` отдельно читает город и наблюдает полный прогноз из Room-backed repository.

Для результата поиска используется отдельный `temporary-forecast/{cityId}`. Маршрут по-прежнему содержит только стабильный ID, а координаты, название и timezone выбранного результата принадлежат navigation host и сохраняются как отдельные `rememberSaveable`-примитивы. Это удерживает выбор при recreation/back stack, не создавая запись в Room. `TemporaryCityForecastViewModel` получает восстановленный снимок через Koin-параметр, запрашивает `CoordinatesWeatherRepository` и не зависит от `FavoriteCitiesRepository`, поэтому просмотр физически не может неявно изменить избранное.

Машина состояния различает:

- `InitialLoading`;
- `Content`;
- `RefreshingWithContent`;
- `ErrorWithoutCache`;
- `ErrorWithCache`;
- `CityNotFound`.

Один `load` создаёт один refresh. Новый запрос через `collectLatest` отменяет старый. После успешной записи UI получает forecast из Room Flow, а не из прямого сетевого return value.

Presentation mapper:

- выбирает до 24 ближайших почасовых точек в timezone города;
- передаёт для почасовой точки короткую локальную подпись времени и нормализованную вероятность осадков; первые шесть реальных точек используются двумя нижними графиками без интерполяции;
- выбирает реальные дни, начиная с сегодняшнего дня города;
- отдаёт первым семи дням вкладку «7 дней»;
- ограничивает «Месяц» первыми 16 реальными точками;
- преобразует Celsius в выбранную единицу локально;
- переводит неизвестный или отсутствующий WMO code в нейтральное `UNKNOWN`/«Неизвестно».

Графики вкладки «Сейчас» являются чистым presentation UI: высота температурных столбцов нормализуется относительно минимума/максимума выбранных шести точек, осадки используют шкалу 0–100 %. Рядом всегда видны числовые значения и время, а Compose semantics объединяет каждую диаграмму в полное текстовое описание. При крупном системном шрифте точки допускают горизонтальную прокрутку, весь экран — вертикальную.

`ForecastScreen` получает фон только из `ForecastUiState`: content, refresh поверх content и cached error сохраняют `current.condition`; состояния без погодных данных используют neutral. Чистая функция `forecastGradientColors` сопоставляет все WMO-группы отдельным смысловым трёхцветным палитрам: солнечная жёлто-золотая, облачная серая, туманная дымчатая, дождливая синяя, снежная ледяная, грозовая фиолетовая. `forecastVisualColors` синхронно выбирает контентные токены: на светлых фонах тёмные текст/иконки и затемнённое стекло, на глубоких фонах — белые. Палитры остаются UI-деталью, не проникают в ViewModel/domain, а погодная иконка и подпись продолжают передавать смысл независимо от цвета.

Для погодной Главной `WeatherStartViewModel` сначала читает реактивный флаг `automaticLocationWeather`. В автоматическом режиме `LocationForecastViewModel` через чистые domain-контракты последовательно просит одну текущую позицию и прогноз по координатам. Android `LocationManager`, системный `Geocoder`, runtime permission и выбор доступного network/GPS provider остаются в data/Route; ViewModel не зависит от Android SDK. Геокодер предпочитает название города, затем район/регион и ограничен таймаутом; его ошибка оставляет название нейтральным, но не отменяет координаты и прогноз. Последний согласованный результат живёт только в памяти singleton-repository и реактивно используется заголовком прогноза и карточкой экрана «Города». Этот путь не пишет город или прогноз в Room/SharedPreferences, а ошибка местоположения/сети превращается в отдельное восстанавливаемое UI-состояние. Параллельные повторные нажатия объединяются в один запрос.

### Настройки

`SettingsRepository` публикует один `StateFlow<UserSettings>`. Реализация инкапсулирует SharedPreferences с ключами `temperature_unit`, `theme_mode` и `automatic_location_weather`, defaults `CELSIUS`/`LIGHT`/`true` и безопасным fallback повреждённых значений.

Выбор единицы или темы обновляет общий flow немедленно. Поэтому:

- корневая тема меняет всё Compose-дерево без recreation маршрута;
- Home и Forecast пересчитывают presentation-модели без сетевого запроса;
- сохранённое значение восстанавливается новым repository после restart процесса;
- переключатель геолокации немедленно меняет источник погодной Главной; включение без разрешения запускает системный запрос, выключение возвращает сохранённый город/пустое состояние.

Очистка вызывается только после AlertDialog confirmation. Repository выполняет один `DELETE FROM favorite_cities`; внешние ключи `ON DELETE CASCADE` в той же Room-операции удаляют current/hourly/daily cache. После commit Room Flow публикует пустой список.

## Offline-first refresh

Обычный refresh города:

```text
cityId
  → прочитать CityEntity из Room
  → запросить Open-Meteo по сохранённым координатам
  → проверить nullable DTO и одинаковые индексы массивов
  → построить полный domain Forecast
  → одной Room-транзакцией заменить current/hourly/daily
  → Room Flow публикует новое состояние UI
```

До начала Room-транзакции DTO полностью преобразуется. Если сеть, parsing или mapping завершились ошибкой, `replaceForecast` не вызывается и старый кэш не меняется. Если одна из Room-вставок падает, транзакция откатывается целиком.

Добавление города использует `INSERT IGNORE` и отдельное обновление metadata. Сначала город сохраняется, затем запрашивается погода. Поэтому неудачный первый прогноз не удаляет избранное, а повторное добавление не создаёт дубль и не стирает прежний кэш.

Для каждого city ID repository содержит ref-counted `Mutex`. Два одинаковых refresh сериализуются; разные города могут обновляться параллельно. `refreshAll()` использует `supervisorScope`, поэтому ошибка одного города не отменяет остальные.

## Room как источник истины

Room schema version 2 содержит:

- `favorite_cities`;
- `current_weather` — максимум одна строка на город;
- `hourly_weather` — упорядоченные точки;
- `daily_weather` — упорядоченные точки.

Все погодные таблицы имеют foreign key на город с `ON DELETE CASCADE`. `CityDao.replaceForecast()` — `@Transaction`: удаляет старые части и вставляет новый полный набор согласованно. Схемы версий 1 и 2 экспортированы в `app/schemas`, а `MIGRATION_1_2` сохраняет прежние города и доступную legacy current-сводку без destructive migration.

Температуры всегда хранятся в Celsius. Отсутствующий WMO code current сохраняется nullable; для исторически non-null hourly/daily колонок используется специальный код `-1`, который общий mapping трактует как `UNKNOWN`.

## Open-Meteo boundary

Приложение использует один `OkHttpClient` и два Retrofit endpoint того же HTTP-стека:

- `https://geocoding-api.open-meteo.com/`;
- `https://api.open-meteo.com/`.

Контракт фиксирует максимум 10 поисковых результатов, `language=ru`, `forecast_days=16`, `timezone=auto` и `temperature_unit=celsius`. API key не используется.

DTO-поля и элементы внешних массивов nullable. Строка hourly/daily создаётся только когда все обязательные значения одного индекса пригодны; отсутствующий weather code является допустимым и отображается как неизвестное состояние. Пустой критический current/hourly/daily набор становится типизированной `EmptyData`, а не правдоподобным нулём.

Data layer различает network, timeout, HTTP, parsing, empty-data и unexpected failures. UI получает только безопасные enum-сообщения и string resources, без HTTP status, exception message или stack trace.

## Koin graph

Агрегирующий `appModule` включает отдельные definition-группы:

- `networkModule`;
- `databaseModule`;
- `repositoryModule`;
- `settingsModule`;
- `viewModelModule`;
- `workerModule`.

Koin создаёт OkHttp/Retrofit/API, Room/DAO, repository implementations, device-location adapter, settings, clock, ViewModel и Worker. `ForecastViewModel` получает `cityId` как runtime parameter; `LocationForecastViewModel` получает абстракции текущих координат и погоды по координатам. Graph verification и runtime smoke test проверяют, что эти зависимости действительно разрешаются.

## WorkManager

`WeatherForecastApplication` запускает Koin с официальным `workManagerFactory()` и затем вызывает `WeatherRefreshScheduler`.

Scheduler регистрирует одну работу с параметрами:

- unique name `favorite-weather-refresh`;
- `ExistingPeriodicWorkPolicy.KEEP`;
- период 3 часа;
- `NetworkType.CONNECTED`;
- exponential backoff 15 минут.

`WeatherRefreshWorker` получает `FavoriteCitiesRepository` конструктором. Он не использует `GlobalContext` или иной service locator.

Политика результата:

| Repository result | WorkManager result |
|---|---|
| success / partial success / empty favorites | `success` |
| полный временный сбой | `retry` |
| постоянный или mixed total failure | `failure` |
| cancellation | пробрасывается владельцу coroutine |

Partial success уже сохраняет успешные города до возврата результата. Ошибка не удаляет старый кэш.

## Навигация и тема

Navigation Compose содержит пять destination для трёх основных пользовательских зон:

- `weather` — стартовая погодная Главная: первый сохранённый город из Room или честное пустое состояние;
- `home` — список избранных и поиск;
- `forecast/{cityId}`;
- `temporary-forecast/{cityId}` — тот же экран Прогноза для saveable-временного результата поиска, без Room-записи;
- `settings`.

`WeatherStartViewModel` наблюдает Room-backed список избранного и выбирает первую запись в стабильном порядке `addedAt, id`. На погодной Главной `ForecastRoute` повторно использует существующий прогноз с ключом ViewModel по city ID; при пустом списке показывает отдельную панель выбора города. Переходы используют `launchSingleTop`, а Back — обычный `navigateUp`, поэтому стартовая Главная не дублируется. Короткие slide/fade transitions находятся только на navigation boundary.

Одна корневая `WeatherForecastTheme` получает `ThemeMode` из `AppThemeViewModel`. Поддерживаются только Light/Dark; системного третьего режима нет. System bar icons синхронизируются с выбранной темой.

## Тестовые границы

- Чистые unit tests: модели, WMO, температура, mapping и ViewModel policy.
- JSON fixtures: полный, пустой, повреждённый/разной длины и отсутствующий weather code.
- Room/Robolectric integration: Flow, transaction rollback, replacement, cascade и migration.
- Compose/Robolectric: реальные stateful Routes/stateless Screens, semantics, retry, tabs, крупный шрифт и анимации.
- Connected local acceptance: реальный NavHost + реальные Routes/ViewModel через Koin с fake repositories выполняют путь от пустой погодной Главной через список/поиск до Прогноза и Настроек, а затем обратно к погоде выбранного города. Отдельные Compose/navigation/ViewModel тесты доказывают временный путь поиска без вызова добавления в избранное.
- WorkManager Test: request constraints, unique schedule и Worker result policy через настоящий KoinWorkerFactory.

Настоящий Open-Meteo не вызывается автоматическими тестами. Device-level визуальная проверка, process restart на ОС, Doze и фактический сетевой transport требуют подключённого устройства/эмулятора и фиксируются отдельно в `RALPH_PROGRESS.md`.

## Безопасность и приватность

- Manifest-граф содержит `INTERNET`, `ACCESS_NETWORK_STATE` и минимальный foreground `ACCESS_COARSE_LOCATION` для WF-026. Merged manifest дополнительно получает стандартные служебные WorkManager permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` и внутреннюю signature-permission); точный/фоновый location, contacts, storage, camera и notification permissions отсутствуют.
- Все endpoints используют HTTPS.
- API keys и пользовательская аутентификация отсутствуют.
- HTTP body logging не включён; координаты и полные ответы не пишутся в release-log.
- Технические exception details не передаются в UI.
- Room и SharedPreferences находятся во внутреннем app storage.
