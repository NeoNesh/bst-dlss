// ============================================================================
// BST DLSS - Native Hardware NVIDIA NGX (DLSS 2/3/4) + WGL_NV_DX_interop2 Bridge
// Supports NVIDIA GeForce RTX 20xx (Turing), 30xx (Ampere), 40xx (Ada), 50xx (Blackwell)
// Zero proprietary redistribution: dynamically binds _nvngx.dll and OTA NGX models
// from the user's installed NVIDIA Graphics Driver.
// ============================================================================

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <gl/GL.h>
#include <d3d11.h>
#include <dxgi.h>
#include <jni.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <wchar.h>

#pragma comment(lib, "opengl32.lib")
#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "version.lib")

// ----------------------------------------------------------------------------
// WGL_NV_DX_interop2 definitions
// ----------------------------------------------------------------------------
#define WGL_ACCESS_READ_ONLY_NV           0x00000000
#define WGL_ACCESS_READ_WRITE_NV          0x00000001
#define WGL_ACCESS_WRITE_DISCARD_NV       0x00000002

typedef HANDLE (WINAPI *PFNWGLDXOPENDEVICENVPROC)(void *dxDevice);
typedef BOOL   (WINAPI *PFNWGLDXCLOSEDEVICENVPROC)(HANDLE hDevice);
typedef HANDLE (WINAPI *PFNWGLDXREGISTEROBJECTNVPROC)(HANDLE hDevice, void *dxObject, GLuint name, GLenum type, GLenum access);
typedef BOOL   (WINAPI *PFNWGLDXUNREGISTEROBJECTNVPROC)(HANDLE hDevice, HANDLE hObject);
typedef BOOL   (WINAPI *PFNWGLDXOBJECTACCESSNVPROC)(HANDLE hObject, GLenum access);
typedef BOOL   (WINAPI *PFNWGLDXLOCKOBJECTSNVPROC)(HANDLE hDevice, GLint count, HANDLE *hObjects);
typedef BOOL   (WINAPI *PFNWGLDXUNLOCKOBJECTSNVPROC)(HANDLE hDevice, GLint count, HANDLE *hObjects);
typedef void   (APIENTRY *PFNGLFLUSHPROC)(void);

// ----------------------------------------------------------------------------
// NVIDIA NGX D3D11 ABI definitions
// ----------------------------------------------------------------------------
typedef uint32_t NVSDK_NGX_Result;
#define NVSDK_NGX_Result_Success 0x00000001U
#define NVSDK_NGX_SUCCEED(v) (((v) & 0xFFF00000U) != 0xBAD00000U && ((v) & 1U) != 0U)

enum NVSDK_NGX_EngineType {
    NVSDK_NGX_ENGINE_TYPE_CUSTOM = 0,
};

enum NVSDK_NGX_Feature {
    NVSDK_NGX_Feature_Reserved0      = 0,
    NVSDK_NGX_Feature_SuperSampling  = 1,
    NVSDK_NGX_Feature_FrameGeneration = 11,
    NVSDK_NGX_Feature_RayReconstruction = 13
};

enum NVSDK_NGX_Version {
    NVSDK_NGX_Version_API = 0x0000015
};

enum NVSDK_NGX_PerfQuality_Value {
    NVSDK_NGX_PerfQuality_Value_MaxPerf          = 0,
    NVSDK_NGX_PerfQuality_Value_Balanced         = 1,
    NVSDK_NGX_PerfQuality_Value_MaxQuality       = 2,
    NVSDK_NGX_PerfQuality_Value_UltraPerformance = 3,
    NVSDK_NGX_PerfQuality_Value_UltraQuality     = 4,
    NVSDK_NGX_PerfQuality_Value_DLAA             = 5
};

// DLSS Feature Create Flags
#define NVSDK_NGX_DLSS_Feature_Flags_IsHDR          (1 << 0)
#define NVSDK_NGX_DLSS_Feature_Flags_MVLowRes       (1 << 1)
#define NVSDK_NGX_DLSS_Feature_Flags_MVJittered     (1 << 2)
#define NVSDK_NGX_DLSS_Feature_Flags_DepthInverted  (1 << 3)
#define NVSDK_NGX_DLSS_Feature_Flags_Reserved_0     (1 << 4)
#define NVSDK_NGX_DLSS_Feature_Flags_DoSharpening   (1 << 5)
#define NVSDK_NGX_DLSS_Feature_Flags_AutoExposure   (1 << 6)

struct NVSDK_NGX_PathListInfo {
    const wchar_t* const* Path;
    unsigned int Length;
};

struct NVSDK_NGX_LoggingInfo {
    void* LoggingCallback;
    int MinimumLoggingLevel;
    bool DisableOtherLoggingSinks;
};

struct NVSDK_NGX_FeatureCommonInfo {
    NVSDK_NGX_PathListInfo PathListInfo;
    void* InternalData;
    NVSDK_NGX_LoggingInfo LoggingInfo;
};

struct NVSDK_NGX_Handle {
    unsigned int Id;
};

struct ID3D12Resource;

// Exact MSVC vtable declaration matching nvsdk_ngx_defs.h
struct NVSDK_NGX_Parameter {
    virtual void Set(const char *InName, unsigned long long InValue) = 0;
    virtual void Set(const char *InName, float InValue) = 0;
    virtual void Set(const char *InName, double InValue) = 0;
    virtual void Set(const char *InName, unsigned int InValue) = 0;
    virtual void Set(const char *InName, int InValue) = 0;
    virtual void Set(const char *InName, ID3D11Resource *InValue) = 0;
    virtual void Set(const char *InName, ID3D12Resource *InValue) = 0;
    virtual void Set(const char *InName, void *InValue) = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, unsigned long long *OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, float *OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, double *OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, unsigned int *OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, int *OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, ID3D11Resource **OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, ID3D12Resource **OutValue) const = 0;
    virtual NVSDK_NGX_Result Get(const char *InName, void **OutValue) const = 0;
    virtual void Reset() = 0;
};

typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_Init_ProjectID)(
    const char *InProjectId,
    NVSDK_NGX_EngineType InEngineType,
    const char *InEngineVersion,
    const wchar_t *InApplicationDataPath,
    ID3D11Device *InDevice,
    NVSDK_NGX_Version InSDKVersion,
    const NVSDK_NGX_FeatureCommonInfo *InFeatureInfo);

typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_GetCapabilityParameters)(NVSDK_NGX_Parameter **OutParams);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_AllocateParameters)(NVSDK_NGX_Parameter **OutParams);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_DestroyParameters)(NVSDK_NGX_Parameter *InParams);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_CreateFeature)(
    ID3D11DeviceContext *InDevCtx,
    NVSDK_NGX_Feature InFeatureID,
    NVSDK_NGX_Parameter *InParams,
    NVSDK_NGX_Handle **OutHandle);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_EvaluateFeature)(
    ID3D11DeviceContext *InDevCtx,
    const NVSDK_NGX_Handle *InFeatureHandle,
    const NVSDK_NGX_Parameter *InParams,
    void *InCallback);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_ReleaseFeature)(NVSDK_NGX_Handle *InHandle);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_Shutdown)(void);
typedef NVSDK_NGX_Result (*PFN_NVSDK_NGX_D3D11_Shutdown1)(ID3D11Device *InDevice);

// ----------------------------------------------------------------------------
// Global Engine State
// ----------------------------------------------------------------------------
static bool g_initialized = false;
static bool g_ngxAvailable = false;
static bool g_frameGenAvailable = false;
static int  g_rtxSeries = 0; // 20, 30, 40, 50
static uint32_t g_vendorId = 0;
static uint32_t g_deviceId = 0;
static char g_gpuName[256] = "Unknown GPU";
static char g_dlssVersion[128] = "v310.6.0.0";
static char g_diagnostics[1024] = "Not initialized";
static wchar_t g_cacheDir[MAX_PATH] = {0};
static wchar_t g_driverStoreDir[MAX_PATH] = {0};

static void ExtractFileVersion(const wchar_t* filePath, char* outBuf, size_t outSize) {
    DWORD handle = 0;
    DWORD verSize = GetFileVersionInfoSizeW(filePath, &handle);
    if (verSize > 0) {
        void* verData = malloc(verSize);
        if (verData) {
            if (GetFileVersionInfoW(filePath, 0, verSize, verData)) {
                VS_FIXEDFILEINFO* ffi = nullptr;
                UINT ffiLen = 0;
                if (VerQueryValueW(verData, L"\\", (LPVOID*)&ffi, &ffiLen) && ffi) {
                    snprintf(outBuf, outSize, "v%u.%u.%u.%u",
                        (unsigned)HIWORD(ffi->dwFileVersionMS),
                        (unsigned)LOWORD(ffi->dwFileVersionMS),
                        (unsigned)HIWORD(ffi->dwFileVersionLS),
                        (unsigned)LOWORD(ffi->dwFileVersionLS));
                }
            }
            free(verData);
        }
    }
}

static ID3D11Device*        g_d3dDevice = nullptr;
static ID3D11DeviceContext* g_d3dContext = nullptr;
static HMODULE              g_hNgxModule = nullptr;

static PFN_NVSDK_NGX_D3D11_Init_ProjectID          g_pfnInitProjectId = nullptr;
static PFN_NVSDK_NGX_D3D11_GetCapabilityParameters g_pfnGetCapParams  = nullptr;
static PFN_NVSDK_NGX_D3D11_AllocateParameters      g_pfnAllocParams   = nullptr;
static PFN_NVSDK_NGX_D3D11_DestroyParameters       g_pfnDestroyParams = nullptr;
static PFN_NVSDK_NGX_D3D11_CreateFeature           g_pfnCreateFeature = nullptr;
static PFN_NVSDK_NGX_D3D11_EvaluateFeature         g_pfnEvaluateFeature = nullptr;
static PFN_NVSDK_NGX_D3D11_ReleaseFeature          g_pfnReleaseFeature = nullptr;
static PFN_NVSDK_NGX_D3D11_Shutdown                g_pfnShutdown      = nullptr;
static PFN_NVSDK_NGX_D3D11_Shutdown1               g_pfnShutdown1     = nullptr;

static PFNWGLDXOPENDEVICENVPROC       g_wglDXOpenDeviceNV       = nullptr;
static PFNWGLDXCLOSEDEVICENVPROC      g_wglDXCloseDeviceNV      = nullptr;
static PFNWGLDXREGISTEROBJECTNVPROC   g_wglDXRegisterObjectNV   = nullptr;
static PFNWGLDXUNREGISTEROBJECTNVPROC g_wglDXUnregisterObjectNV = nullptr;
static PFNWGLDXOBJECTACCESSNVPROC     g_wglDXObjectAccessNV     = nullptr;
static PFNWGLDXLOCKOBJECTSNVPROC      g_wglDXLockObjectsNV      = nullptr;
static PFNWGLDXUNLOCKOBJECTSNVPROC    g_wglDXUnlockObjectsNV    = nullptr;

static NVSDK_NGX_Parameter* g_ngxParams = nullptr;
static NVSDK_NGX_Handle*    g_dlssHandle = nullptr;

static HANDLE           g_hInteropDevice = nullptr;
static ID3D11Texture2D* g_d3dTextures[4] = { nullptr, nullptr, nullptr, nullptr }; // Color, Depth, MV, Output
static HANDLE           g_hInteropObjs[4] = { nullptr, nullptr, nullptr, nullptr };
static GLuint           g_glTextures[4]   = { 0, 0, 0, 0 };
static bool             g_objectsLockedInGL = false;
static UINT             g_renderWidth = 0;
static UINT             g_renderHeight = 0;
static UINT             g_outWidth = 0;
static UINT             g_outHeight = 0;
static LARGE_INTEGER    g_qpcFreq = {0};

// ----------------------------------------------------------------------------
// Helper: Detect RTX Series (20xx, 30xx, 40xx, 50xx) from Name & PCI DeviceId
// ----------------------------------------------------------------------------
static int ClassifyRtxSeries(const char* name, uint32_t vendorId, uint32_t deviceId) {
    if (vendorId != 0x10DE) return 0;

    if (strstr(name, "RTX 50") || strstr(name, "RTX 5")) return 50;
    if (strstr(name, "RTX 40") || strstr(name, "RTX 4")) return 40;
    if (strstr(name, "RTX 30") || strstr(name, "RTX 3") || strstr(name, "RTX A")) return 30;
    if (strstr(name, "RTX 20") || strstr(name, "TITAN RTX") || strstr(name, "Quadro RTX")) return 20;

    // PCI Device ID fallback for NVIDIA architectures:
    // Blackwell (RTX 50xx): 0x2B00 - 0x2FFF
    if (deviceId >= 0x2B00 && deviceId <= 0x2FFF) return 50;
    // Ada Lovelace (RTX 40xx): 0x2680 - 0x28FF
    if (deviceId >= 0x2680 && deviceId <= 0x28FF) return 40;
    // Ampere (RTX 30xx): 0x2200 - 0x25FF
    if (deviceId >= 0x2200 && deviceId <= 0x25FF) return 30;
    // Turing (RTX 20xx): 0x1E00 - 0x1F9F
    if (deviceId >= 0x1E00 && deviceId <= 0x1F9F) return 20;

    return 0;
}

// ----------------------------------------------------------------------------
// Helper: Discover and stage nvngx_dlss.dll from NVIDIA OTA ProgramData or DriverStore
// ----------------------------------------------------------------------------
static bool StageDriverDlssBinary(const wchar_t* targetCacheDir) {
    CreateDirectoryW(targetCacheDir, nullptr);

    wchar_t dstDllPath[MAX_PATH];
    wsprintfW(dstDllPath, L"%s\\nvngx_dlss.dll", targetCacheDir);

    // If already staged and non-empty, check if valid
    WIN32_FILE_ATTRIBUTE_DATA fad;
    if (GetFileAttributesExW(dstDllPath, GetFileExInfoStandard, &fad) && fad.nFileSizeLow > 1024 * 1024) {
        ExtractFileVersion(dstDllPath, g_dlssVersion, sizeof(g_dlssVersion));
        return true;
    }

    // 1. Check DriverStore directly in case nvngx_dlss.dll is there
    if (g_driverStoreDir[0] != 0) {
        wchar_t driverDlss[MAX_PATH];
        wsprintfW(driverDlss, L"%s\\nvngx_dlss.dll", g_driverStoreDir);
        if (GetFileAttributesW(driverDlss) != INVALID_FILE_ATTRIBUTES) {
            if (CopyFileW(driverDlss, dstDllPath, FALSE)) {
                ExtractFileVersion(dstDllPath, g_dlssVersion, sizeof(g_dlssVersion));
                return true;
            }
        }
    }

    // 2. Scan C:\ProgramData\NVIDIA\NGX\models\dlss\versions\*\files\*.bin
    // Pick the file from the highest version folder that starts with 'MZ' and exports NVSDK_NGX_D3D11_EvaluateFeature
    const wchar_t* modelsRoot = L"C:\\ProgramData\\NVIDIA\\NGX\\models\\dlss\\versions";
    wchar_t searchMask[MAX_PATH];
    wsprintfW(searchMask, L"%s\\*", modelsRoot);

    WIN32_FIND_DATAW fdVer;
    HANDLE hFindVer = FindFirstFileW(searchMask, &fdVer);
    if (hFindVer == INVALID_HANDLE_VALUE) return false;

    wchar_t bestBinPath[MAX_PATH] = {0};
    unsigned long long bestVerNum = 0;

    do {
        if (!(fdVer.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (fdVer.cFileName[0] == L'.') continue;

        unsigned long long verNum = _wcstoui64(fdVer.cFileName, nullptr, 10);
        wchar_t filesMask[MAX_PATH];
        wsprintfW(filesMask, L"%s\\%s\\files\\*.bin", modelsRoot, fdVer.cFileName);

        WIN32_FIND_DATAW fdBin;
        HANDLE hFindBin = FindFirstFileW(filesMask, &fdBin);
        if (hFindBin != INVALID_HANDLE_VALUE) {
            do {
                if (fdBin.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) continue;
                if (fdBin.nFileSizeLow < 2 * 1024 * 1024) continue; // at least 2 MB

                wchar_t candidatePath[MAX_PATH];
                wsprintfW(candidatePath, L"%s\\%s\\files\\%s", modelsRoot, fdVer.cFileName, fdBin.cFileName);

                // Verify MZ header
                HANDLE hFile = CreateFileW(candidatePath, GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
                if (hFile != INVALID_HANDLE_VALUE) {
                    char magic[2] = {0};
                    DWORD readBytes = 0;
                    ReadFile(hFile, magic, 2, &readBytes, nullptr);
                    CloseHandle(hFile);
                    if (readBytes == 2 && magic[0] == 'M' && magic[1] == 'Z') {
                        if (verNum >= bestVerNum) {
                            bestVerNum = verNum;
                            wcscpy_s(bestBinPath, candidatePath);
                        }
                    }
                }
            } while (FindNextFileW(hFindBin, &fdBin));
            FindClose(hFindBin);
        }
    } while (FindNextFileW(hFindVer, &fdVer));
    FindClose(hFindVer);

    if (bestBinPath[0] != 0) {
        if (CopyFileW(bestBinPath, dstDllPath, FALSE)) {
            ExtractFileVersion(dstDllPath, g_dlssVersion, sizeof(g_dlssVersion));
            return true;
        }
    }
    return false;
}

// ----------------------------------------------------------------------------
// Helper: Release current DLSS session & WGL interop objects
// ----------------------------------------------------------------------------
static void ReleaseSessionObjects() {
    glFlush();
    if (g_hInteropDevice && g_objectsLockedInGL && g_wglDXUnlockObjectsNV) {
        g_wglDXUnlockObjectsNV(g_hInteropDevice, 4, g_hInteropObjs);
        g_objectsLockedInGL = false;
    }
    for (int i = 0; i < 4; i++) {
        if (g_hInteropDevice && g_hInteropObjs[i] && g_wglDXUnregisterObjectNV) {
            g_wglDXUnregisterObjectNV(g_hInteropDevice, g_hInteropObjs[i]);
            g_hInteropObjs[i] = nullptr;
        }
        if (g_glTextures[i] != 0) {
            glDeleteTextures(1, &g_glTextures[i]);
            g_glTextures[i] = 0;
        }
        if (g_d3dTextures[i]) {
            g_d3dTextures[i]->Release();
            g_d3dTextures[i] = nullptr;
        }
    }
    if (g_dlssHandle && g_pfnReleaseFeature) {
        if (g_d3dContext) g_d3dContext->Flush();
        g_pfnReleaseFeature(g_dlssHandle);
        g_dlssHandle = nullptr;
    }
}

// ----------------------------------------------------------------------------
// JNI Exports for dev.bstdlss.nativebridge.BstDlssNative
// ----------------------------------------------------------------------------
extern "C" {

JNIEXPORT jint JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeInit(JNIEnv* env, jclass clazz, jstring jCacheDir) {
    if (g_initialized) {
        return g_ngxAvailable ? 1 : 0;
    }

    QueryPerformanceFrequency(&g_qpcFreq);

    if (jCacheDir != nullptr) {
        const jchar* chars = env->GetStringChars(jCacheDir, nullptr);
        jsize len = env->GetStringLength(jCacheDir);
        int copyLen = (len < (MAX_PATH - 1)) ? len : (MAX_PATH - 1);
        wcsncpy_s(g_cacheDir, (const wchar_t*)chars, copyLen);
        g_cacheDir[copyLen] = L'\0';
        env->ReleaseStringChars(jCacheDir, chars);
    } else {
        GetTempPathW(MAX_PATH, g_cacheDir);
    }

    // 1. Enumerate DXGI Adapters & find NVIDIA GPU
    IDXGIFactory1* factory = nullptr;
    HRESULT hr = CreateDXGIFactory1(__uuidof(IDXGIFactory1), (void**)&factory);
    if (FAILED(hr) || !factory) {
        snprintf(g_diagnostics, sizeof(g_diagnostics), "CreateDXGIFactory1 failed: 0x%08X", (unsigned)hr);
        return -1;
    }

    IDXGIAdapter1* selectedAdapter = nullptr;
    IDXGIAdapter1* adapter = nullptr;
    for (UINT i = 0; factory->EnumAdapters1(i, &adapter) != DXGI_ERROR_NOT_FOUND; ++i) {
        DXGI_ADAPTER_DESC1 desc;
        adapter->GetDesc1(&desc);
        char nameBuf[256] = {0};
        WideCharToMultiByte(CP_UTF8, 0, desc.Description, -1, nameBuf, sizeof(nameBuf) - 1, nullptr, nullptr);

        if (selectedAdapter == nullptr || desc.VendorId == 0x10DE) {
            if (selectedAdapter) selectedAdapter->Release();
            selectedAdapter = adapter;
            g_vendorId = desc.VendorId;
            g_deviceId = desc.DeviceId;
            strncpy_s(g_gpuName, nameBuf, sizeof(g_gpuName) - 1);
            if (desc.VendorId == 0x10DE) break;
        } else {
            adapter->Release();
        }
    }
    factory->Release();

    g_rtxSeries = ClassifyRtxSeries(g_gpuName, g_vendorId, g_deviceId);

    // 2. Create hardware D3D11 Device on the NVIDIA adapter
    D3D_FEATURE_LEVEL featureLevels[] = { D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0 };
    D3D_FEATURE_LEVEL createdFl;
    hr = D3D11CreateDevice(
        selectedAdapter,
        selectedAdapter ? D3D_DRIVER_TYPE_UNKNOWN : D3D_DRIVER_TYPE_HARDWARE,
        nullptr,
        0,
        featureLevels,
        2,
        D3D11_SDK_VERSION,
        &g_d3dDevice,
        &createdFl,
        &g_d3dContext
    );
    if (selectedAdapter) selectedAdapter->Release();

    if (FAILED(hr) || !g_d3dDevice) {
        snprintf(g_diagnostics, sizeof(g_diagnostics), "D3D11CreateDevice failed: 0x%08X | GPU: %s", (unsigned)hr, g_gpuName);
        return -2;
    }

    // 3. Locate NVIDIA NGX Core directory from Windows Registry
    DWORD cb = sizeof(g_driverStoreDir);
    LSTATUS regStatus = RegGetValueW(
        HKEY_LOCAL_MACHINE,
        L"SOFTWARE\\NVIDIA Corporation\\Global\\NGXCore",
        L"FullPath",
        RRF_RT_REG_SZ,
        nullptr,
        g_driverStoreDir,
        &cb
    );
    if (regStatus != ERROR_SUCCESS || g_driverStoreDir[0] == 0) {
        cb = sizeof(g_driverStoreDir);
        RegGetValueW(
            HKEY_LOCAL_MACHINE,
            L"SYSTEM\\CurrentControlSet\\Services\\nvlddmkm\\NGXCore",
            L"NGXPath",
            RRF_RT_REG_SZ,
            nullptr,
            g_driverStoreDir,
            &cb
        );
    }

    // Check Frame Generation DLL presence (supported on RTX 40xx and 50xx)
    if (g_driverStoreDir[0] != 0) {
        wchar_t fgPath[MAX_PATH];
        wsprintfW(fgPath, L"%s\\nvngx_dlssg.dll", g_driverStoreDir);
        if (GetFileAttributesW(fgPath) != INVALID_FILE_ATTRIBUTES && g_rtxSeries >= 40) {
            g_frameGenAvailable = true;
        }
    }

    // Stage nvngx_dlss.dll into g_cacheDir
    bool stagedDlss = StageDriverDlssBinary(g_cacheDir);

    // 4. Load _nvngx.dll from DriverStore
    if (g_driverStoreDir[0] != 0) {
        wchar_t ngxDllPath[MAX_PATH];
        wsprintfW(ngxDllPath, L"%s\\_nvngx.dll", g_driverStoreDir);
        g_hNgxModule = LoadLibraryExW(ngxDllPath, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH);
        if (!g_hNgxModule) {
            wsprintfW(ngxDllPath, L"%s\\nvngx.dll", g_driverStoreDir);
            g_hNgxModule = LoadLibraryExW(ngxDllPath, nullptr, LOAD_WITH_ALTERED_SEARCH_PATH);
        }
    }

    if (g_hNgxModule) {
        g_pfnInitProjectId   = (PFN_NVSDK_NGX_D3D11_Init_ProjectID)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_Init_ProjectID");
        g_pfnGetCapParams    = (PFN_NVSDK_NGX_D3D11_GetCapabilityParameters)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_GetCapabilityParameters");
        g_pfnAllocParams     = (PFN_NVSDK_NGX_D3D11_AllocateParameters)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_AllocateParameters");
        g_pfnDestroyParams   = (PFN_NVSDK_NGX_D3D11_DestroyParameters)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_DestroyParameters");
        g_pfnCreateFeature   = (PFN_NVSDK_NGX_D3D11_CreateFeature)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_CreateFeature");
        g_pfnEvaluateFeature = (PFN_NVSDK_NGX_D3D11_EvaluateFeature)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_EvaluateFeature");
        g_pfnReleaseFeature  = (PFN_NVSDK_NGX_D3D11_ReleaseFeature)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_ReleaseFeature");
        g_pfnShutdown        = (PFN_NVSDK_NGX_D3D11_Shutdown)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_Shutdown");
        g_pfnShutdown1       = (PFN_NVSDK_NGX_D3D11_Shutdown1)GetProcAddress(g_hNgxModule, "NVSDK_NGX_D3D11_Shutdown1");

        if (g_pfnInitProjectId && g_pfnAllocParams && g_pfnCreateFeature && g_pfnEvaluateFeature) {
            const wchar_t* searchPaths[2] = { g_cacheDir, g_driverStoreDir };
            NVSDK_NGX_FeatureCommonInfo fci = {};
            fci.PathListInfo.Path = searchPaths;
            fci.PathListInfo.Length = 2;

            NVSDK_NGX_Result initRes = g_pfnInitProjectId(
                "a0f57b54-1daf-4934-90ae-c4035c19df04",
                NVSDK_NGX_ENGINE_TYPE_CUSTOM,
                "1.0.0",
                g_cacheDir,
                g_d3dDevice,
                NVSDK_NGX_Version_API,
                &fci
            );

            if (NVSDK_NGX_SUCCEED(initRes)) {
                NVSDK_NGX_Result allocRes = g_pfnAllocParams(&g_ngxParams);
                if (NVSDK_NGX_SUCCEED(allocRes) && g_ngxParams != nullptr) {
                    g_ngxAvailable = true;
                } else if (g_pfnGetCapParams) {
                    NVSDK_NGX_Result capRes = g_pfnGetCapParams(&g_ngxParams);
                    if (NVSDK_NGX_SUCCEED(capRes) && g_ngxParams != nullptr) {
                        g_ngxAvailable = true;
                    }
                }
            }
            snprintf(
                g_diagnostics,
                sizeof(g_diagnostics),
                "GPU: %s (RTX %dxx, DevID=0x%04X) | NGX Init=0x%08X | DLSS Staged=%s | Hardware NGX=%s | FrameGen=%s",
                g_gpuName,
                g_rtxSeries,
                g_deviceId,
                initRes,
                stagedDlss ? "YES" : "NO",
                g_ngxAvailable ? "ACTIVE (Tensor Cores)" : "FALLBACK",
                g_frameGenAvailable ? "SUPPORTED" : "N/A"
            );
        }
    } else {
        snprintf(
            g_diagnostics,
            sizeof(g_diagnostics),
            "GPU: %s (RTX %dxx, DevID=0x%04X) | _nvngx.dll not found in DriverStore | Shader Upscaler Active",
            g_gpuName,
            g_rtxSeries,
            g_deviceId
        );
    }

    g_initialized = true;
    return g_ngxAvailable ? 1 : 0;
}

JNIEXPORT jstring JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeGetGpuName(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(g_gpuName);
}

JNIEXPORT jstring JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeGetDlssVersion(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(g_dlssVersion);
}

JNIEXPORT jint JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeGetRtxSeries(JNIEnv* env, jclass clazz) {
    return (jint)g_rtxSeries;
}

JNIEXPORT jboolean JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeIsNgxDlssAvailable(JNIEnv* env, jclass clazz) {
    return g_ngxAvailable ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeIsFrameGenAvailable(JNIEnv* env, jclass clazz) {
    return g_frameGenAvailable ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeGetOptimalResolution(
    JNIEnv* env, jclass clazz, jint outWidth, jint outHeight, jint qualityMode
) {
    float scale = 0.6666667f;
    switch (qualityMode) {
        case NVSDK_NGX_PerfQuality_Value_UltraPerformance: scale = 0.3333333f; break; // 3.0x
        case NVSDK_NGX_PerfQuality_Value_MaxPerf:          scale = 0.5000000f; break; // 2.0x
        case NVSDK_NGX_PerfQuality_Value_Balanced:         scale = 0.5800000f; break; // 1.72x
        case NVSDK_NGX_PerfQuality_Value_MaxQuality:       scale = 0.6666667f; break; // 1.5x
        case NVSDK_NGX_PerfQuality_Value_UltraQuality:     scale = 0.7700000f; break; // 1.3x
        case NVSDK_NGX_PerfQuality_Value_DLAA:             scale = 1.0000000f; break; // 1.0x
        default:                                           scale = 0.6666667f; break;
    }
    jint rw = (jint)((outWidth * scale) + 0.5f);
    jint rh = (jint)((outHeight * scale) + 0.5f);
    // Align to even pixel dimensions for DLSS Tensor Cores
    rw = (rw & ~1);
    rh = (rh & ~1);
    if (rw < 64) rw = 64;
    if (rh < 64) rh = 64;

    jint result[2] = { rw, rh };
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, result);
    return arr;
}

JNIEXPORT jintArray JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeCreateSession(
    JNIEnv* env, jclass clazz,
    jint renderWidth, jint renderHeight,
    jint outWidth, jint outHeight,
    jint qualityMode, jint preset
) {
    if (!g_initialized || !g_ngxAvailable || !g_d3dDevice || !g_d3dContext || !g_ngxParams) {
        return nullptr;
    }

    // Bind WGL_NV_DX_interop2 functions on the active OpenGL render thread
    if (!g_wglDXOpenDeviceNV) {
        g_wglDXOpenDeviceNV       = (PFNWGLDXOPENDEVICENVPROC)wglGetProcAddress("wglDXOpenDeviceNV");
        g_wglDXCloseDeviceNV      = (PFNWGLDXCLOSEDEVICENVPROC)wglGetProcAddress("wglDXCloseDeviceNV");
        g_wglDXRegisterObjectNV   = (PFNWGLDXREGISTEROBJECTNVPROC)wglGetProcAddress("wglDXRegisterObjectNV");
        g_wglDXUnregisterObjectNV = (PFNWGLDXUNREGISTEROBJECTNVPROC)wglGetProcAddress("wglDXUnregisterObjectNV");
        g_wglDXObjectAccessNV     = (PFNWGLDXOBJECTACCESSNVPROC)wglGetProcAddress("wglDXObjectAccessNV");
        g_wglDXLockObjectsNV      = (PFNWGLDXLOCKOBJECTSNVPROC)wglGetProcAddress("wglDXLockObjectsNV");
        g_wglDXUnlockObjectsNV    = (PFNWGLDXUNLOCKOBJECTSNVPROC)wglGetProcAddress("wglDXUnlockObjectsNV");
    }

    if (!g_wglDXOpenDeviceNV || !g_wglDXRegisterObjectNV || !g_wglDXLockObjectsNV || !g_wglDXUnlockObjectsNV) {
        snprintf(g_diagnostics, sizeof(g_diagnostics), "WGL_NV_DX_interop2 not available on current OpenGL context");
        return nullptr;
    }

    if (!g_hInteropDevice) {
        g_hInteropDevice = g_wglDXOpenDeviceNV(g_d3dDevice);
        if (!g_hInteropDevice) {
            snprintf(g_diagnostics, sizeof(g_diagnostics), "wglDXOpenDeviceNV failed (err=%lu)", GetLastError());
            return nullptr;
        }
    }

    ReleaseSessionObjects();

    g_renderWidth  = (UINT)renderWidth;
    g_renderHeight = (UINT)renderHeight;
    g_outWidth     = (UINT)outWidth;
    g_outHeight    = (UINT)outHeight;

    // 1. Create the 4 D3D11 textures (Color, Depth, MotionVectors, Output)
    auto createD3DTex = [](UINT w, UINT h, DXGI_FORMAT fmt, UINT bindFlags) -> ID3D11Texture2D* {
        D3D11_TEXTURE2D_DESC d = {};
        d.Width = w;
        d.Height = h;
        d.MipLevels = 1;
        d.ArraySize = 1;
        d.Format = fmt;
        d.SampleDesc.Count = 1;
        d.Usage = D3D11_USAGE_DEFAULT;
        d.BindFlags = bindFlags;
        ID3D11Texture2D* tex = nullptr;
        g_d3dDevice->CreateTexture2D(&d, nullptr, &tex);
        return tex;
    };

    g_d3dTextures[0] = createD3DTex(g_renderWidth, g_renderHeight, DXGI_FORMAT_R8G8B8A8_UNORM, D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET);
    g_d3dTextures[1] = createD3DTex(g_renderWidth, g_renderHeight, DXGI_FORMAT_R32_FLOAT,      D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET);
    g_d3dTextures[2] = createD3DTex(g_renderWidth, g_renderHeight, DXGI_FORMAT_R16G16_FLOAT,   D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET);
    g_d3dTextures[3] = createD3DTex(g_outWidth,    g_outHeight,    DXGI_FORMAT_R8G8B8A8_UNORM, D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET | D3D11_BIND_UNORDERED_ACCESS);

    for (int i = 0; i < 4; i++) {
        if (!g_d3dTextures[i]) {
            ReleaseSessionObjects();
            return nullptr;
        }
    }

    // 2. Configure and Create NVIDIA NGX DLSS SuperSampling Feature on Tensor Cores
    g_ngxParams->Set("CreationNodeMask", 1);
    g_ngxParams->Set("VisibilityNodeMask", 1);
    g_ngxParams->Set("Width", g_renderWidth);
    g_ngxParams->Set("Height", g_renderHeight);
    g_ngxParams->Set("OutWidth", g_outWidth);
    g_ngxParams->Set("OutHeight", g_outHeight);

    int ngxQuality = (int)qualityMode;
    // Map Ultra Quality (4) or out-of-range custom scale to MaxQuality (2) so NGX validates successfully
    if (ngxQuality < 0 || ngxQuality > 5 || ngxQuality == 4) {
        ngxQuality = NVSDK_NGX_PerfQuality_Value_MaxQuality;
    }
    g_ngxParams->Set("PerfQualityValue", ngxQuality);

    int createFlags = NVSDK_NGX_DLSS_Feature_Flags_MVLowRes | NVSDK_NGX_DLSS_Feature_Flags_AutoExposure;
    g_ngxParams->Set("DLSS.Feature.Create.Flags", createFlags);

    if (preset > 0 && preset <= 16) {
        g_ngxParams->Set("DLSS.Hint.Render.Preset.DLAA", (unsigned int)preset);
        g_ngxParams->Set("DLSS.Hint.Render.Preset.Quality", (unsigned int)preset);
        g_ngxParams->Set("DLSS.Hint.Render.Preset.Balanced", (unsigned int)preset);
        g_ngxParams->Set("DLSS.Hint.Render.Preset.Performance", (unsigned int)preset);
        g_ngxParams->Set("DLSS.Hint.Render.Preset.UltraPerformance", (unsigned int)preset);
    }

    NVSDK_NGX_Result createRes = g_pfnCreateFeature(g_d3dContext, NVSDK_NGX_Feature_SuperSampling, g_ngxParams, &g_dlssHandle);
    if (!NVSDK_NGX_SUCCEED(createRes) || !g_dlssHandle) {
        snprintf(g_diagnostics, sizeof(g_diagnostics), "NVSDK_NGX_D3D11_CreateFeature failed: 0x%08X (%ux%u -> %ux%u)",
                 createRes, g_renderWidth, g_renderHeight, g_outWidth, g_outHeight);
        ReleaseSessionObjects();
        return nullptr;
    }

    // 3. Register D3D11 textures with OpenGL via WGL_NV_DX_interop2
    glGenTextures(4, g_glTextures);
    for (int i = 0; i < 4; i++) {
        g_hInteropObjs[i] = g_wglDXRegisterObjectNV(g_hInteropDevice, g_d3dTextures[i], g_glTextures[i], GL_TEXTURE_2D, WGL_ACCESS_READ_WRITE_NV);
        if (!g_hInteropObjs[i]) {
            snprintf(g_diagnostics, sizeof(g_diagnostics), "wglDXRegisterObjectNV failed on slot %d", i);
            ReleaseSessionObjects();
            return nullptr;
        }
    }

    // Lock for OpenGL rendering initially and configure texture filtering so GLSL sampling is complete
    if (g_wglDXLockObjectsNV(g_hInteropDevice, 4, g_hInteropObjs)) {
        g_objectsLockedInGL = true;
        for (int i = 0; i < 4; i++) {
            glBindTexture(GL_TEXTURE_2D, g_glTextures[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, 0x812F); // GL_CLAMP_TO_EDGE
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, 0x812F); // GL_CLAMP_TO_EDGE
        }
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    snprintf(
        g_diagnostics,
        sizeof(g_diagnostics),
        "DLSS Session Active: %ux%u -> %ux%u (Quality=%d, Preset=%d) on %s (RTX %dxx Tensor Cores)",
        g_renderWidth, g_renderHeight, g_outWidth, g_outHeight, qualityMode, preset, g_gpuName, g_rtxSeries
    );

    jint outIds[4] = { (jint)g_glTextures[0], (jint)g_glTextures[1], (jint)g_glTextures[2], (jint)g_glTextures[3] };
    jintArray arr = env->NewIntArray(4);
    env->SetIntArrayRegion(arr, 0, 4, outIds);
    return arr;
}

JNIEXPORT jlong JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeEvaluateDlss(
    JNIEnv* env, jclass clazz,
    jfloat jitterX, jfloat jitterY,
    jfloat mvScaleX, jfloat mvScaleY,
    jfloat sharpness, jboolean reset,
    jfloat preExposure, jfloat frameTimeDeltaMs
) {
    if (!g_dlssHandle || !g_hInteropDevice || !g_d3dContext || !g_ngxParams) {
        return -1;
    }

    LARGE_INTEGER t0, t1;
    QueryPerformanceCounter(&t0);

    // 1. Ensure all OpenGL draw operations (MRT pass) are submitted to GPU before Direct3D reads them
    glFlush();

    // 2. Unlock shared textures from OpenGL so D3D11 / NGX Tensor Cores can access them
    if (g_objectsLockedInGL) {
        if (!g_wglDXUnlockObjectsNV(g_hInteropDevice, 4, g_hInteropObjs)) {
            return -2;
        }
        g_objectsLockedInGL = false;
    }

    g_ngxParams->Set("Color",         (ID3D11Resource*)g_d3dTextures[0]);
    g_ngxParams->Set("Depth",         (ID3D11Resource*)g_d3dTextures[1]);
    g_ngxParams->Set("MotionVectors", (ID3D11Resource*)g_d3dTextures[2]);
    g_ngxParams->Set("Output",        (ID3D11Resource*)g_d3dTextures[3]);

    g_ngxParams->Set("Jitter.Offset.X", (float)jitterX);
    g_ngxParams->Set("Jitter.Offset.Y", (float)jitterY);
    g_ngxParams->Set("MV.Scale.X",      (float)mvScaleX);
    g_ngxParams->Set("MV.Scale.Y",      (float)mvScaleY);
    g_ngxParams->Set("Sharpness",       (float)sharpness);
    g_ngxParams->Set("Reset",           reset ? 1 : 0);
    g_ngxParams->Set("PreExposure",     (float)(preExposure > 0.0f ? preExposure : 1.0f));
    g_ngxParams->Set("FrameTimeDeltaInMsec", (float)(frameTimeDeltaMs > 0.0f ? frameTimeDeltaMs : 16.6f));
    g_ngxParams->Set("DLSS.Render.Subrect.Dimensions.Width",  g_renderWidth);
    g_ngxParams->Set("DLSS.Render.Subrect.Dimensions.Height", g_renderHeight);

    NVSDK_NGX_Result evalRes = g_pfnEvaluateFeature(g_d3dContext, g_dlssHandle, g_ngxParams, nullptr);

    // 3. Flush D3D11 command queue to ensure Tensor Core kernel evaluation is submitted before OpenGL accesses Output
    if (g_d3dContext) {
        g_d3dContext->Flush();
    }

    // 4. Lock shared textures back to OpenGL
    if (g_wglDXLockObjectsNV(g_hInteropDevice, 4, g_hInteropObjs)) {
        g_objectsLockedInGL = true;
    }

    if (!NVSDK_NGX_SUCCEED(evalRes)) {
        return -3;
    }

    QueryPerformanceCounter(&t1);
    jlong elapsedUs = (jlong)(((t1.QuadPart - t0.QuadPart) * 1000000LL) / (g_qpcFreq.QuadPart ? g_qpcFreq.QuadPart : 1));
    return elapsedUs;
}

JNIEXPORT jstring JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeGetDiagnostics(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(g_diagnostics);
}

JNIEXPORT void JNICALL Java_dev_bstdlss_nativebridge_BstDlssNative_nativeShutdown(JNIEnv* env, jclass clazz) {
    ReleaseSessionObjects();
    if (g_hInteropDevice && g_wglDXCloseDeviceNV) {
        g_wglDXCloseDeviceNV(g_hInteropDevice);
        g_hInteropDevice = nullptr;
    }
    if (g_ngxParams && g_pfnDestroyParams) {
        g_pfnDestroyParams(g_ngxParams);
        g_ngxParams = nullptr;
    }
    if (g_pfnShutdown1 && g_d3dDevice) {
        g_pfnShutdown1(g_d3dDevice);
    } else if (g_pfnShutdown) {
        g_pfnShutdown();
    }
    if (g_d3dContext) {
        g_d3dContext->Flush();
        g_d3dContext->Release();
        g_d3dContext = nullptr;
    }
    if (g_d3dDevice) {
        g_d3dDevice->Release();
        g_d3dDevice = nullptr;
    }
    if (g_hNgxModule) {
        FreeLibrary(g_hNgxModule);
        g_hNgxModule = nullptr;
    }
    g_initialized = false;
    g_ngxAvailable = false;
}

} // extern "C"
