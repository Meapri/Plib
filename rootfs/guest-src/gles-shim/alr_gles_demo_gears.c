#include <stdio.h>
#include <stdlib.h>

#define EGL_CONTEXT_CLIENT_VERSION 0x3098
#define EGL_NONE 0x3038
#define GL_COLOR_BUFFER_BIT 0x00004000u
#define GL_FLOAT 0x1406u
#define GL_TRIANGLES 0x0004u
#define GL_VERTEX_SHADER 0x8B31u
#define GL_FRAGMENT_SHADER 0x8B30u
#define GL_COMPILE_STATUS 0x8B81u
#define GL_LINK_STATUS 0x8B82u

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

void glViewport(GLint, GLint, GLsizei, GLsizei);
void glClearColor(float, float, float, float);
void glClear(GLenum);
GLuint glCreateShader(GLenum);
void glShaderSource(GLuint, GLsizei, const char* const*, const GLint*);
void glCompileShader(GLuint);
void glGetShaderiv(GLuint, GLenum, GLint*);
GLuint glCreateProgram(void);
void glAttachShader(GLuint, GLuint);
void glBindAttribLocation(GLuint, GLuint, const char*);
void glLinkProgram(GLuint);
void glGetProgramiv(GLuint, GLenum, GLint*);
void glUseProgram(GLuint);
void glUniform4f(GLint, float, float, float, float);
void glEnableVertexAttribArray(GLuint);
void glVertexAttribPointer(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
void glDrawArrays(GLenum, GLint, GLsizei);

static int requested_frame_count(void) {
    const char* value = getenv("ALR_GLES_DEMO_FRAME_COUNT");
    if (value == 0 || value[0] == 0) return 60;
    int count = atoi(value);
    if (count < 1) return 1;
    if (count > 240) return 240;
    return count;
}

static int require_step(int condition, const char* name) {
    printf("ALR_GLES_DEMO_STEP %s %s\n", name, condition ? "ok" : "fail");
    return condition;
}

static GLuint compile_shader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, 0);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    return compiled ? shader : 0;
}

static GLuint build_program(void) {
    static const char* vertex_source =
        "attribute vec2 aPosition;\n"
        "void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }\n";
    static const char* fragment_source =
        "precision mediump float;\n"
        "uniform vec4 uColor;\n"
        "void main() { gl_FragColor = uColor; }\n";
    GLuint vertex = compile_shader(GL_VERTEX_SHADER, vertex_source);
    GLuint fragment = compile_shader(GL_FRAGMENT_SHADER, fragment_source);
    if (vertex == 0 || fragment == 0) return 0;
    GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glBindAttribLocation(program, 0, "aPosition");
    glLinkProgram(program);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    return linked ? program : 0;
}

int main(void) {
    printf("alr guest gles demo gears ok\n");
    printf("ALR_GLES_DEMO_KIND es2gears-like-triangle-strip-subset\n");

    EGLDisplay display = eglGetDisplay(0);
    EGLConfig config = 0;
    EGLint config_count = 0;
    const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    EGLSurface surface = (EGLSurface)0x3001;
    EGLContext context = 0;
    int ok = 1;

    ok &= require_step(display != 0, "eglGetDisplay");
    ok &= require_step(eglInitialize(display, 0, 0), "eglInitialize");
    ok &= require_step(eglChooseConfig(display, 0, &config, 1, &config_count) && config != 0, "eglChooseConfig");
    context = eglCreateContext(display, config, 0, context_attribs);
    ok &= require_step(context != 0, "eglCreateContext");
    ok &= require_step(eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent");

    GLuint program = build_program();
    ok &= require_step(program != 0, "shaderProgram");
    glUseProgram(program);
    printf("ALR_GLES_DEMO_STEP glUseProgram ok\n");

    glViewport(0, 0, 256, 144);
    printf("ALR_GLES_DEMO_STEP glViewport ok\n");
    glEnableVertexAttribArray(0);
    printf("ALR_GLES_DEMO_STEP glEnableVertexAttribArray ok\n");

    int frames = requested_frame_count();
    int submitted = 0;
    for (int frame = 1; frame <= frames; ++frame) {
        const float wobble = (float)((frame * 37) % 100) / 100.0f;
        const float vertices[] = {
            0.0f, 0.74f - wobble * 0.08f,
            -0.70f + wobble * 0.04f, -0.60f,
            0.70f - wobble * 0.04f, -0.60f,
        };
        float red = (float)((frame * 19) % 100) / 100.0f;
        float green = (float)((frame * 31) % 100) / 100.0f;
        float blue = (float)((frame * 43) % 100) / 100.0f;
        glClearColor(0.02f, 0.03f, 0.05f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUniform4f(0, red, green, blue, 1.0f);
        glVertexAttribPointer(0, 2, GL_FLOAT, 0, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        if (!eglSwapBuffers(display, surface)) {
            ok = 0;
            break;
        }
        ++submitted;
    }

    printf("ALR_GLES_DEMO_WORKLOAD requested=%d submitted=%d\n", frames, submitted);
    ok &= require_step(submitted == frames, "frameLoop");
    ok &= require_step(eglDestroyContext(display, context), "eglDestroyContext");
    ok &= require_step(eglTerminate(display), "eglTerminate");
    return ok ? 0 : 5;
}
