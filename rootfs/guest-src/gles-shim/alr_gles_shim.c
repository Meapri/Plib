#include <stdio.h>

#define ALR_GL_COLOR_BUFFER_BIT 0x00004000u

static float g_clear_red = 0.10f;
static float g_clear_green = 0.34f;
static float g_clear_blue = 0.78f;
static float g_clear_alpha = 1.0f;
static int g_initialized = 0;
static int g_context_created = 0;
static int g_current = 0;
static int g_clear_count = 0;
static int g_swap_count = 0;

const char* alr_gles_shim_version(void) {
    return "alr-gles-shim egl-gles-subset-v2";
}

void* alr_egl_get_display(void* native_display) {
    (void)native_display;
    return (void*)0x1001;
}

int alr_egl_initialize(void* display, int* major, int* minor) {
    if (display == 0) return 0;
    if (major != 0) *major = 1;
    if (minor != 0) *minor = 5;
    g_initialized = 1;
    return 1;
}

int alr_egl_choose_config(void* display, void* config_out) {
    if (!g_initialized || display == 0) return 0;
    if (config_out != 0) {
        void** out = (void**)config_out;
        *out = (void*)0x1002;
    }
    return 1;
}

void* alr_egl_create_context(void* display, void* config) {
    if (!g_initialized || display == 0 || config == 0) return 0;
    g_context_created = 1;
    return (void*)0x2001;
}

int alr_egl_make_current(void* display, void* draw, void* read, void* context) {
    (void)draw;
    (void)read;
    if (!g_context_created || display == 0 || context == 0) return 0;
    g_current = 1;
    return 1;
}

void alr_gl_viewport(int x, int y, int width, int height) {
    printf("ALR_GLES_API_VIEWPORT %d %d %d %d\n", x, y, width, height);
}

void alr_gl_clear_color(float red, float green, float blue, float alpha) {
    g_clear_red = red;
    g_clear_green = green;
    g_clear_blue = blue;
    g_clear_alpha = alpha;
}

int alr_gl_clear(unsigned int mask) {
    if (!g_current || (mask & ALR_GL_COLOR_BUFFER_BIT) == 0) return 0;
    ++g_clear_count;
    return 1;
}

int alr_egl_swap_buffers(void* display, void* surface) {
    (void)surface;
    if (!g_current || display == 0 || g_clear_count <= g_swap_count) return 0;
    ++g_swap_count;
    printf(
        "ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR %.2f %.2f %.2f shim-frame-%04d\n",
        g_clear_red,
        g_clear_green,
        g_clear_blue,
        g_swap_count
    );
    fflush(stdout);
    return 1;
}

int alr_egl_destroy_context(void* display, void* context) {
    if (display == 0 || context == 0) return 0;
    g_context_created = 0;
    g_current = 0;
    return 1;
}

int alr_egl_terminate(void* display) {
    if (display == 0) return 0;
    g_initialized = 0;
    return 1;
}

int alr_gles_submit_clear(float red, float green, float blue, const char* tag) {
    (void)tag;
    void* display = alr_egl_get_display(0);
    void* config = 0;
    void* context = 0;
    if (!alr_egl_initialize(display, 0, 0)) return 0;
    if (!alr_egl_choose_config(display, &config)) return 0;
    context = alr_egl_create_context(display, config);
    if (context == 0) return 0;
    if (!alr_egl_make_current(display, (void*)0x3001, (void*)0x3001, context)) return 0;
    alr_gl_viewport(0, 0, 128, 72);
    alr_gl_clear_color(red, green, blue, 1.0f);
    if (!alr_gl_clear(ALR_GL_COLOR_BUFFER_BIT)) return 0;
    if (!alr_egl_swap_buffers(display, (void*)0x3001)) return 0;
    alr_egl_destroy_context(display, context);
    alr_egl_terminate(display);
    return 1;
}
