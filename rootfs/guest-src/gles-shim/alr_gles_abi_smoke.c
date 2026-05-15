#include <stdio.h>

#define EGL_OPENGL_ES2_BIT 0x0004
#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_NONE 0x3038
#define GL_COLOR_BUFFER_BIT 0x00004000u
#define GL_FLOAT 0x1406u
#define GL_TRIANGLES 0x0004u

typedef void* EGLDisplay;
typedef void* EGLConfig;
typedef void* EGLContext;
typedef void* EGLSurface;
typedef int EGLBoolean;
typedef int EGLint;
typedef unsigned int GLenum;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef unsigned char GLboolean;

EGLDisplay eglGetDisplay(void*);
EGLBoolean eglInitialize(EGLDisplay, EGLint*, EGLint*);
EGLBoolean eglChooseConfig(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
EGLContext eglCreateContext(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
EGLBoolean eglMakeCurrent(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
EGLBoolean eglSwapBuffers(EGLDisplay, EGLSurface);
EGLBoolean eglDestroyContext(EGLDisplay, EGLContext);
EGLBoolean eglTerminate(EGLDisplay);
void* eglGetProcAddress(const char*);

void glViewport(GLint, GLint, GLsizei, GLsizei);
void glClearColor(float, float, float, float);
void glClear(GLenum);
void glUseProgram(GLuint);
void glEnableVertexAttribArray(GLuint);
void glVertexAttribPointer(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
void glDrawArrays(GLenum, GLint, GLsizei);

static int require_step(int condition, const char* name) {
    printf("ALR_GLES_ABI_STEP %s %s\n", name, condition ? "ok" : "fail");
    return condition;
}

int main(void) {
    printf("alr guest gles abi smoke ok\n");
    printf("ALR_GLES_ABI_LIBS visible libEGL.so libGLESv2.so\n");

    EGLDisplay display = eglGetDisplay(0);
    EGLint major = 0;
    EGLint minor = 0;
    EGLConfig config = 0;
    EGLint config_count = 0;
    const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    EGLContext context = 0;
    EGLSurface surface = (EGLSurface)0x3001;
    int ok = 1;

    ok &= require_step(display != 0, "eglGetDisplay");
    ok &= require_step(eglInitialize(display, &major, &minor), "eglInitialize");
    printf("ALR_GLES_ABI_EGL_VERSION %d.%d\n", major, minor);
    ok &= require_step(eglChooseConfig(display, 0, &config, 1, &config_count) && config != 0 && config_count >= 1, "eglChooseConfig");
    context = eglCreateContext(display, config, 0, context_attribs);
    ok &= require_step(context != 0, "eglCreateContext");
    ok &= require_step(eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent");
    ok &= require_step(eglGetProcAddress("glDrawArrays") != 0, "eglGetProcAddress");

    glViewport(0, 0, 128, 72);
    printf("ALR_GLES_ABI_STEP glViewport ok\n");
    glClearColor(0.14f, 0.30f, 0.74f, 1.0f);
    printf("ALR_GLES_ABI_STEP glClearColor ok\n");
    glClear(GL_COLOR_BUFFER_BIT);
    printf("ALR_GLES_ABI_STEP glClear ok\n");
    ok &= require_step(eglSwapBuffers(display, surface), "eglSwapBuffersClear");

    static const float vertices[] = {
        0.0f, 0.72f,
        -0.68f, -0.58f,
        0.68f, -0.58f,
    };
    glUseProgram(1);
    printf("ALR_GLES_ABI_STEP glUseProgram ok\n");
    glEnableVertexAttribArray(0);
    printf("ALR_GLES_ABI_STEP glEnableVertexAttribArray ok\n");
    glVertexAttribPointer(0, 2, GL_FLOAT, 0, 0, vertices);
    printf("ALR_GLES_ABI_STEP glVertexAttribPointer ok\n");
    glDrawArrays(GL_TRIANGLES, 0, 3);
    printf("ALR_GLES_ABI_STEP glDrawArrays ok\n");
    ok &= require_step(eglSwapBuffers(display, surface), "eglSwapBuffersDraw");

    ok &= require_step(eglDestroyContext(display, context), "eglDestroyContext");
    ok &= require_step(eglTerminate(display), "eglTerminate");

    return ok ? 0 : 4;
}
