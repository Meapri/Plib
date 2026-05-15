#include <stdio.h>
#include <string.h>

#define ALR_GL_COLOR_BUFFER_BIT 0x00004000u
#define ALR_GL_FLOAT 0x1406u
#define ALR_GL_TRIANGLES 0x0004u

static float g_clear_red = 0.10f;
static float g_clear_green = 0.34f;
static float g_clear_blue = 0.78f;
static float g_clear_alpha = 1.0f;
static float g_draw_red = 0.95f;
static float g_draw_green = 0.72f;
static float g_draw_blue = 0.18f;
static int g_initialized = 0;
static int g_context_created = 0;
static int g_current = 0;
static int g_program_selected = 0;
static int g_vertex_attrib_enabled = 0;
static int g_vertex_attrib_pointer = 0;
static int g_pending_clear = 0;
static int g_pending_draw = 0;
static int g_clear_count = 0;
static int g_swap_count = 0;

void* eglGetDisplay(void* native_display);
int eglInitialize(void* display, int* major, int* minor);
int eglChooseConfig(void* display, const int* attrib_list, void** configs, int config_size, int* num_config);
void* eglCreateContext(void* display, void* config, void* share_context, const int* attrib_list);
int eglMakeCurrent(void* display, void* draw, void* read, void* context);
int eglSwapBuffers(void* display, void* surface);
int eglDestroyContext(void* display, void* context);
int eglTerminate(void* display);
void glViewport(int x, int y, int width, int height);
void glClearColor(float red, float green, float blue, float alpha);
void glClear(unsigned int mask);
void glUseProgram(unsigned int program);
void glEnableVertexAttribArray(unsigned int index);
void glVertexAttribPointer(unsigned int index, int size, unsigned int type, unsigned char normalized, int stride, const void* pointer);
void glDrawArrays(unsigned int mode, int first, int count);

const char* alr_gles_shim_version(void) {
    return "alr-gles-shim egl-gles-subset-v3-triangle";
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
    g_pending_clear = 1;
    g_pending_draw = 0;
    return 1;
}

int alr_gl_use_program(unsigned int program) {
    if (!g_current || program == 0) return 0;
    g_program_selected = 1;
    return 1;
}

int alr_gl_enable_vertex_attrib_array(unsigned int index) {
    if (!g_current || index > 15) return 0;
    g_vertex_attrib_enabled = 1;
    return 1;
}

int alr_gl_vertex_attrib_pointer(
    unsigned int index,
    int size,
    unsigned int type,
    int normalized,
    int stride,
    const void* pointer
) {
    (void)normalized;
    (void)stride;
    if (!g_current || index > 15 || size < 2 || size > 4 || type != ALR_GL_FLOAT || pointer == 0) return 0;
    g_vertex_attrib_pointer = 1;
    return 1;
}

void alr_gl_draw_color(float red, float green, float blue) {
    g_draw_red = red;
    g_draw_green = green;
    g_draw_blue = blue;
}

int alr_gl_draw_arrays(unsigned int mode, int first, int count) {
    if (!g_current || !g_program_selected || !g_vertex_attrib_enabled || !g_vertex_attrib_pointer) return 0;
    if (mode != ALR_GL_TRIANGLES || first != 0 || count < 3) return 0;
    g_pending_draw = 1;
    return 1;
}

int alr_egl_swap_buffers(void* display, void* surface) {
    (void)surface;
    if (!g_current || display == 0 || (!g_pending_clear && !g_pending_draw)) return 0;
    ++g_swap_count;
    if (g_pending_draw) {
        printf(
            "ALR_GLES_SHIM_COMMAND ALR_GPU_DRAW_TRIANGLE %.2f %.2f %.2f shim-draw-frame-%04d\n",
            g_draw_red,
            g_draw_green,
            g_draw_blue,
            g_swap_count
        );
    } else {
        printf(
            "ALR_GLES_SHIM_COMMAND ALR_GPU_CLEAR %.2f %.2f %.2f shim-frame-%04d\n",
            g_clear_red,
            g_clear_green,
            g_clear_blue,
            g_swap_count
        );
    }
    g_pending_clear = 0;
    g_pending_draw = 0;
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

int alr_gles_submit_triangle(float red, float green, float blue, const char* tag) {
    (void)tag;
    static const float vertices[] = {
        0.0f, 0.72f,
        -0.68f, -0.58f,
        0.68f, -0.58f,
    };
    void* display = alr_egl_get_display(0);
    void* config = 0;
    void* context = 0;
    if (!alr_egl_initialize(display, 0, 0)) return 0;
    if (!alr_egl_choose_config(display, &config)) return 0;
    context = alr_egl_create_context(display, config);
    if (context == 0) return 0;
    if (!alr_egl_make_current(display, (void*)0x3001, (void*)0x3001, context)) return 0;
    alr_gl_viewport(0, 0, 128, 72);
    if (!alr_gl_use_program(1)) return 0;
    if (!alr_gl_enable_vertex_attrib_array(0)) return 0;
    if (!alr_gl_vertex_attrib_pointer(0, 2, ALR_GL_FLOAT, 0, 0, vertices)) return 0;
    alr_gl_draw_color(red, green, blue);
    if (!alr_gl_draw_arrays(ALR_GL_TRIANGLES, 0, 3)) return 0;
    if (!alr_egl_swap_buffers(display, (void*)0x3001)) return 0;
    alr_egl_destroy_context(display, context);
    alr_egl_terminate(display);
    return 1;
}

void* eglGetDisplay(void* native_display) {
    return alr_egl_get_display(native_display);
}

int eglInitialize(void* display, int* major, int* minor) {
    return alr_egl_initialize(display, major, minor);
}

int eglChooseConfig(void* display, const int* attrib_list, void** configs, int config_size, int* num_config) {
    (void)attrib_list;
    if (num_config != 0) *num_config = 1;
    if (config_size <= 0 || configs == 0) return g_initialized && display != 0;
    return alr_egl_choose_config(display, configs);
}

void* eglCreateContext(void* display, void* config, void* share_context, const int* attrib_list) {
    (void)share_context;
    (void)attrib_list;
    return alr_egl_create_context(display, config);
}

int eglMakeCurrent(void* display, void* draw, void* read, void* context) {
    return alr_egl_make_current(display, draw, read, context);
}

int eglSwapBuffers(void* display, void* surface) {
    return alr_egl_swap_buffers(display, surface);
}

int eglDestroyContext(void* display, void* context) {
    return alr_egl_destroy_context(display, context);
}

int eglTerminate(void* display) {
    return alr_egl_terminate(display);
}

void* eglGetProcAddress(const char* procname) {
    if (procname == 0) return 0;
    if (strcmp(procname, "eglGetDisplay") == 0) return (void*)eglGetDisplay;
    if (strcmp(procname, "eglInitialize") == 0) return (void*)eglInitialize;
    if (strcmp(procname, "eglChooseConfig") == 0) return (void*)eglChooseConfig;
    if (strcmp(procname, "eglCreateContext") == 0) return (void*)eglCreateContext;
    if (strcmp(procname, "eglMakeCurrent") == 0) return (void*)eglMakeCurrent;
    if (strcmp(procname, "eglSwapBuffers") == 0) return (void*)eglSwapBuffers;
    if (strcmp(procname, "eglDestroyContext") == 0) return (void*)eglDestroyContext;
    if (strcmp(procname, "eglTerminate") == 0) return (void*)eglTerminate;
    if (strcmp(procname, "glViewport") == 0) return (void*)glViewport;
    if (strcmp(procname, "glClearColor") == 0) return (void*)glClearColor;
    if (strcmp(procname, "glClear") == 0) return (void*)glClear;
    if (strcmp(procname, "glUseProgram") == 0) return (void*)glUseProgram;
    if (strcmp(procname, "glEnableVertexAttribArray") == 0) return (void*)glEnableVertexAttribArray;
    if (strcmp(procname, "glVertexAttribPointer") == 0) return (void*)glVertexAttribPointer;
    if (strcmp(procname, "glDrawArrays") == 0) return (void*)glDrawArrays;
    return 0;
}

void glViewport(int x, int y, int width, int height) {
    alr_gl_viewport(x, y, width, height);
}

void glClearColor(float red, float green, float blue, float alpha) {
    alr_gl_clear_color(red, green, blue, alpha);
}

void glClear(unsigned int mask) {
    (void)alr_gl_clear(mask);
}

void glUseProgram(unsigned int program) {
    (void)alr_gl_use_program(program);
}

void glEnableVertexAttribArray(unsigned int index) {
    (void)alr_gl_enable_vertex_attrib_array(index);
}

void glVertexAttribPointer(unsigned int index, int size, unsigned int type, unsigned char normalized, int stride, const void* pointer) {
    (void)alr_gl_vertex_attrib_pointer(index, size, type, normalized ? 1 : 0, stride, pointer);
}

void glDrawArrays(unsigned int mode, int first, int count) {
    (void)alr_gl_draw_arrays(mode, first, count);
}
