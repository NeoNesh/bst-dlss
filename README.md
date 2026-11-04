# BST DLSS — NVIDIA RTX 20xx–50xx Hardware Upscaler (Forge 1.18.2 – 26.3)

**BST DLSS** — автономный клиентский мод для **Minecraft Forge (`1.18.2` — `26.3`)** без сторонних зависимостей, активирующий настоящий аппаратный **NVIDIA DLSS Super Resolution** и **DLAA** на тензорных ядрах видеокарт серий **GeForce RTX 20xx (Turing)**, **RTX 30xx (Ampere)**, **RTX 40xx (Ada Lovelace)** и **RTX 50xx (Blackwell)**.

---

## Ключевые особенности (Только собственные технологии, 0 зависимостей)

1. **Настоящий аппаратный NVIDIA NGX DLSS через `WGL_NV_DX_interop2`:**
   - Нативный мост `bst_dlss_native.dll` создаёт аппаратное устройство **Direct3D 11** на видеокарте NVIDIA RTX и связывает текстуры OpenGL (`GL_RGBA8`, `GL_R32F`, `GL_RG16F`) напрямую с видеопамятью D3D11 без копирования через CPU (`wglDXOpenDeviceNV` / `wglDXRegisterObjectNV` / `wglDXLockObjectsNV`).
   - Автоматически обнаруживает и подключает системный драйвер `_nvngx.dll` (`HKLM\SOFTWARE\NVIDIA Corporation\Global\NGXCore`) и актуальную модель нейросети DLSS из кэша драйвера (`C:\ProgramData\NVIDIA\NGX\models\dlss\versions`), поэтому мод **не содержит проприетарных бинарников NVIDIA внутри JAR** и полностью соответствует правилам публикации на **Modrinth**.
2. **Поддержка всех поколений RTX (`20xx` – `50xx`):**
   - **RTX 20xx (Turing)**: DLSS Super Resolution + DLAA (Preset C / D / E / J / K)
   - **RTX 30xx (Ampere)**: DLSS Super Resolution + DLAA
   - **RTX 40xx (Ada Lovelace)**: DLSS Super Resolution + DLAA + Frame Generation
   - **RTX 50xx (Blackwell)**: DLSS 4 Super Resolution + DLAA + Frame Generation
3. **Генерация векторов движения (6-DOF Motion Vectors) и Camera Jitter:**
   - Встроенный MRT-шейдер вычисляет экранные векторы движения (`GL_RG16F`) и линейную глубину (`GL_R32F`) из буфера глубины Minecraft и перемещения камеры, применяя субпиксельное смещение `Halton(2, 3)`.
   - 3D-мир рендерится в пониженном внутреннем разрешении (`33%` – `77%`), апскейлится на тензорных ядрах RTX, а интерфейс (HUD, прицел, чат, инвентарь) рисуется поверх в **100% родном разрешении**.
4. **Единый универсальный JAR для Forge `1.18.2` — `26.3`:**
   - Динамический мост `McRuntimeBridge` автоматически определяет как **SRG-маппинги (`1.18.2` – `1.20.4`)**, так и **Mojang Official маппинги (`1.20.5` – `1.21.x` – `26.3`)**.

---

## Управление в игре (Горячие клавиши)

| Клавиша | Действие |
|---|---|
| **`F8`** | Открыть / закрыть интерактивную панель управления **BST DLSS RTX Control Panel** |
| **`F9`** | Быстро переключить режим качества DLSS (`Off` → `Ultra Performance` → `Performance` → `Balanced` → `Quality` → `Ultra Quality` → `DLAA`) |
| **`F10`** | Показать / скрыть оверлей телеметрии RTX (FPS, задержка Tensor Cores в мс, разрешение, активный пресет) |

---

## Сборка проекта из исходного кода

### 1. Сборка нативного C++ JNI-моста (`bst_dlss_native.dll`)
Требуется Visual Studio 2022 Build Tools (MSVC x64) и JDK 17+:
```bat
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cl /nologo /O2 /EHsc /LD /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" native\src\bst_dlss_native.cpp /Fe:src\main\resources\natives\win64\bst_dlss_native.dll
```

### 2. Сборка универсального Forge JAR
```bat
gradle build
```
Готовый файл мода для публикации на **Modrinth** создаётся по пути:
`build/libs/bst-dlss-forge-1.0.0-mc1.18.2-26.3.jar`
