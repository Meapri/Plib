#include <stdio.h>
#include <stdlib.h>

#define ALR_GL_COLOR_BUFFER_BIT 0x00004000u
#define ALR_GL_FLOAT 0x1406u
#define ALR_GL_TRIANGLES 0x0004u

const char* alr_gles_shim_version(void);
void* alr_egl_get_display(void*);
int alr_egl_initialize(void*, int*, int*);
int alr_egl_choose_config(void*, void*);
void* alr_egl_create_context(void*, void*);
int alr_egl_make_current(void*, void*, void*, void*);
void alr_gl_viewport(int, int, int, int);
void alr_gl_clear_color(float, float, float, float);
int alr_gl_clear(unsigned int);
int alr_gl_use_program(unsigned int);
int alr_gl_enable_vertex_attrib_array(unsigned int);
int alr_gl_vertex_attrib_pointer(unsigned int, int, unsigned int, int, int, const void*);
void alr_gl_draw_color(float, float, float);
int alr_gl_draw_arrays(unsigned int, int, int);
int alr_egl_swap_buffers(void*, void*);
int alr_egl_destroy_context(void*, void*);
int alr_egl_terminate(void*);
int alr_gles_submit_clear(float, float, float, const char*);
int alr_gles_submit_triangle(float, float, float, const char*);

static int require_step(int condition, const char* name) {
    printf("ALR_GLES_API_STEP %s %s\n", name, condition ? "ok" : "fail");
    return condition;
}

static int requested_frame_count(void) {
    const char* value = getenv("ALR_GLES_SHIM_FRAME_COUNT");
    if (value == 0 || value[0] == 0) return 1;
    int count = atoi(value);
    if (count < 1) return 1;
    if (count > 120) return 120;
    return count;
}

static int compat_submit_enabled(void) {
    const char* value = getenv("ALR_GLES_COMPAT_SUBMIT");
    return !(value != 0 && value[0] == '0' && value[1] == 0);
}

static int requested_draw_frame_count(void) {
    const char* value = getenv("ALR_GLES_DRAW_FRAME_COUNT");
    if (value == 0 || value[0] == 0) return compat_submit_enabled() ? 1 : 0;
    int count = atoi(value);
    if (count < 0) return 0;
    if (count > 120) return 120;
    return count;
}

int main(void) {
    printf("alr guest gles shim smoke ok\n");
    printf("ALR_GLES_SHIM_LOAD ok version=%s\n", alr_gles_shim_version());

    static const float triangle_vertices[] = {
        0.0f, 0.72f,
        -0.68f, -0.58f,
        0.68f, -0.58f,
    };
    void* display = alr_egl_get_display(0);
    int major = 0;
    int minor = 0;
    void* config = 0;
    void* context = 0;
    int ok = 1;
    ok &= require_step(display != 0, "eglGetDisplay");
    ok &= require_step(alr_egl_initialize(display, &major, &minor), "eglInitialize");
    printf("ALR_GLES_API_EGL_VERSION %d.%d\n", major, minor);
    ok &= require_step(alr_egl_choose_config(display, &config) && config != 0, "eglChooseConfig");
    context = alr_egl_create_context(display, config);
    ok &= require_step(context != 0, "eglCreateContext");
    ok &= require_step(alr_egl_make_current(display, (void*)0x3001, (void*)0x3001, context), "eglMakeCurrent");
    alr_gl_viewport(0, 0, 128, 72);
    printf("ALR_GLES_API_STEP glViewport ok\n");
    alr_gl_clear_color(0.10f, 0.34f, 0.78f, 1.0f);
    printf("ALR_GLES_API_STEP glClearColor ok\n");
    ok &= require_step(alr_gl_clear(ALR_GL_COLOR_BUFFER_BIT), "glClear");
    ok &= require_step(alr_egl_swap_buffers(display, (void*)0x3001), "eglSwapBuffers");
    int frame_count = requested_frame_count();
    for (int frame = 2; frame <= frame_count; ++frame) {
        float red = (float)((frame * 17) % 100) / 100.0f;
        float green = (float)((frame * 29) % 100) / 100.0f;
        float blue = (float)((frame * 43) % 100) / 100.0f;
        alr_gl_clear_color(red, green, blue, 1.0f);
        ok &= alr_gl_clear(ALR_GL_COLOR_BUFFER_BIT);
        ok &= alr_egl_swap_buffers(display, (void*)0x3001);
    }
    printf("ALR_GLES_FRAME_WORKLOAD requested=%d submitted=%d\n", frame_count, frame_count);
    int draw_frame_count = requested_draw_frame_count();
    for (int frame = 1; frame <= draw_frame_count; ++frame) {
        float red = (float)((frame * 31) % 100) / 100.0f;
        float green = (float)((frame * 47) % 100) / 100.0f;
        float blue = (float)((frame * 59) % 100) / 100.0f;
        ok &= require_step(alr_gl_use_program(1), "glUseProgram");
        ok &= require_step(alr_gl_enable_vertex_attrib_array(0), "glEnableVertexAttribArray");
        ok &= require_step(alr_gl_vertex_attrib_pointer(0, 2, ALR_GL_FLOAT, 0, 0, triangle_vertices), "glVertexAttribPointer");
        alr_gl_draw_color(red, green, blue);
        ok &= require_step(alr_gl_draw_arrays(ALR_GL_TRIANGLES, 0, 3), "glDrawArrays");
        ok &= alr_egl_swap_buffers(display, (void*)0x3001);
    }
    printf("ALR_GLES_DRAW_WORKLOAD requested=%d submitted=%d\n", draw_frame_count, draw_frame_count);
    ok &= require_step(alr_egl_destroy_context(display, context), "eglDestroyContext");
    ok &= require_step(alr_egl_terminate(display), "eglTerminate");

    int submit = compat_submit_enabled() ? alr_gles_submit_clear(0.18f, 0.49f, 0.86f, "shim-frame-compat") : 1;
    printf("ALR_GLES_SHIM_SUBMIT rc=%d\n", submit);
    int triangle_submit = compat_submit_enabled() ? alr_gles_submit_triangle(0.92f, 0.62f, 0.18f, "shim-triangle-compat") : 1;
    printf("ALR_GLES_SHIM_TRIANGLE_SUBMIT rc=%d\n", triangle_submit);
    return (ok && submit && triangle_submit) ? 0 : 3;
}
