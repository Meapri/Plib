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

typedef void (*PFN_glViewport)(GLint, GLint, GLsizei, GLsizei);
typedef void (*PFN_glClearColor)(float, float, float, float);
typedef void (*PFN_glClear)(GLenum);
typedef GLuint (*PFN_glCreateShader)(GLenum);
typedef void (*PFN_glShaderSource)(GLuint, GLsizei, const char* const*, const GLint*);
typedef void (*PFN_glCompileShader)(GLuint);
typedef void (*PFN_glGetShaderiv)(GLuint, GLenum, GLint*);
typedef GLuint (*PFN_glCreateProgram)(void);
typedef void (*PFN_glAttachShader)(GLuint, GLuint);
typedef void (*PFN_glBindAttribLocation)(GLuint, GLuint, const char*);
typedef void (*PFN_glLinkProgram)(GLuint);
typedef void (*PFN_glGetProgramiv)(GLuint, GLenum, GLint*);
typedef void (*PFN_glUseProgram)(GLuint);
typedef void (*PFN_glUniform4f)(GLint, float, float, float, float);
typedef void (*PFN_glEnableVertexAttribArray)(GLuint);
typedef void (*PFN_glVertexAttribPointer)(GLuint, GLint, GLenum, GLboolean, GLsizei, const void*);
typedef void (*PFN_glDrawArrays)(GLenum, GLint, GLsizei);

EGLDisplay eglGetDisplay(void*);
EGLBoolean eglInitialize(EGLDisplay, EGLint*, EGLint*);
EGLBoolean eglChooseConfig(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
EGLContext eglCreateContext(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
EGLBoolean eglMakeCurrent(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
EGLBoolean eglSwapBuffers(EGLDisplay, EGLSurface);
EGLBoolean eglDestroyContext(EGLDisplay, EGLContext);
EGLBoolean eglTerminate(EGLDisplay);
void* eglGetProcAddress(const char*);

struct GlesFns {
    PFN_glViewport Viewport;
    PFN_glClearColor ClearColor;
    PFN_glClear Clear;
    PFN_glCreateShader CreateShader;
    PFN_glShaderSource ShaderSource;
    PFN_glCompileShader CompileShader;
    PFN_glGetShaderiv GetShaderiv;
    PFN_glCreateProgram CreateProgram;
    PFN_glAttachShader AttachShader;
    PFN_glBindAttribLocation BindAttribLocation;
    PFN_glLinkProgram LinkProgram;
    PFN_glGetProgramiv GetProgramiv;
    PFN_glUseProgram UseProgram;
    PFN_glUniform4f Uniform4f;
    PFN_glEnableVertexAttribArray EnableVertexAttribArray;
    PFN_glVertexAttribPointer VertexAttribPointer;
    PFN_glDrawArrays DrawArrays;
};

static int requested_frame_count(void) {
    const char* value = getenv("ALR_GLES_PROC_DEMO_FRAME_COUNT");
    if (value == 0 || value[0] == 0) return 45;
    int count = atoi(value);
    if (count < 1) return 1;
    if (count > 240) return 240;
    return count;
}

static int require_step(int condition, const char* name) {
    printf("ALR_GLES_PROC_DEMO_STEP %s %s\n", name, condition ? "ok" : "fail");
    return condition;
}

static int load_proc(void** target, const char* name) {
    *target = eglGetProcAddress(name);
    printf("ALR_GLES_PROC_DEMO_PROC %s %s\n", name, *target != 0 ? "ok" : "missing");
    return *target != 0;
}

static int load_gles_functions(struct GlesFns* gl) {
    int ok = 1;
    ok &= load_proc((void**)&gl->Viewport, "glViewport");
    ok &= load_proc((void**)&gl->ClearColor, "glClearColor");
    ok &= load_proc((void**)&gl->Clear, "glClear");
    ok &= load_proc((void**)&gl->CreateShader, "glCreateShader");
    ok &= load_proc((void**)&gl->ShaderSource, "glShaderSource");
    ok &= load_proc((void**)&gl->CompileShader, "glCompileShader");
    ok &= load_proc((void**)&gl->GetShaderiv, "glGetShaderiv");
    ok &= load_proc((void**)&gl->CreateProgram, "glCreateProgram");
    ok &= load_proc((void**)&gl->AttachShader, "glAttachShader");
    ok &= load_proc((void**)&gl->BindAttribLocation, "glBindAttribLocation");
    ok &= load_proc((void**)&gl->LinkProgram, "glLinkProgram");
    ok &= load_proc((void**)&gl->GetProgramiv, "glGetProgramiv");
    ok &= load_proc((void**)&gl->UseProgram, "glUseProgram");
    ok &= load_proc((void**)&gl->Uniform4f, "glUniform4f");
    ok &= load_proc((void**)&gl->EnableVertexAttribArray, "glEnableVertexAttribArray");
    ok &= load_proc((void**)&gl->VertexAttribPointer, "glVertexAttribPointer");
    ok &= load_proc((void**)&gl->DrawArrays, "glDrawArrays");
    return ok;
}

static GLuint compile_shader(struct GlesFns* gl, GLenum type, const char* source) {
    GLuint shader = gl->CreateShader(type);
    gl->ShaderSource(shader, 1, &source, 0);
    gl->CompileShader(shader);
    GLint compiled = 0;
    gl->GetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    return compiled ? shader : 0;
}

static GLuint build_program(struct GlesFns* gl) {
    static const char* vertex_source =
        "attribute vec2 aPosition;\n"
        "void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }\n";
    static const char* fragment_source =
        "precision mediump float;\n"
        "uniform vec4 uColor;\n"
        "void main() { gl_FragColor = uColor; }\n";
    GLuint vertex = compile_shader(gl, GL_VERTEX_SHADER, vertex_source);
    GLuint fragment = compile_shader(gl, GL_FRAGMENT_SHADER, fragment_source);
    if (vertex == 0 || fragment == 0) return 0;
    GLuint program = gl->CreateProgram();
    gl->AttachShader(program, vertex);
    gl->AttachShader(program, fragment);
    gl->BindAttribLocation(program, 0, "aPosition");
    gl->LinkProgram(program);
    GLint linked = 0;
    gl->GetProgramiv(program, GL_LINK_STATUS, &linked);
    return linked ? program : 0;
}

int main(void) {
    printf("alr guest gles procaddr demo ok\n");
    printf("ALR_GLES_PROC_DEMO_KIND eglGetProcAddress-es2-subset\n");

    EGLDisplay display = eglGetDisplay(0);
    EGLConfig config = 0;
    EGLint config_count = 0;
    const EGLint context_attribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE,
    };
    EGLSurface surface = (EGLSurface)0x3001;
    EGLContext context = 0;
    struct GlesFns gl = {0};
    int ok = 1;

    ok &= require_step(display != 0, "eglGetDisplay");
    ok &= require_step(eglInitialize(display, 0, 0), "eglInitialize");
    ok &= require_step(eglChooseConfig(display, 0, &config, 1, &config_count) && config != 0, "eglChooseConfig");
    context = eglCreateContext(display, config, 0, context_attribs);
    ok &= require_step(context != 0, "eglCreateContext");
    ok &= require_step(eglMakeCurrent(display, surface, surface, context), "eglMakeCurrent");
    ok &= require_step(load_gles_functions(&gl), "eglGetProcAddressAll");

    GLuint program = build_program(&gl);
    ok &= require_step(program != 0, "shaderProgram");
    gl.UseProgram(program);
    gl.Viewport(0, 0, 256, 144);
    gl.EnableVertexAttribArray(0);

    int frames = requested_frame_count();
    int submitted = 0;
    for (int frame = 1; frame <= frames; ++frame) {
        const float wobble = (float)((frame * 23) % 100) / 100.0f;
        const float vertices[] = {
            0.0f, 0.72f,
            -0.62f - wobble * 0.08f, -0.56f,
            0.62f + wobble * 0.08f, -0.56f,
        };
        float red = (float)((frame * 13) % 100) / 100.0f;
        float green = (float)((frame * 41) % 100) / 100.0f;
        float blue = (float)((frame * 67) % 100) / 100.0f;
        gl.ClearColor(0.01f, 0.02f, 0.04f, 1.0f);
        gl.Clear(GL_COLOR_BUFFER_BIT);
        gl.Uniform4f(0, red, green, blue, 1.0f);
        gl.VertexAttribPointer(0, 2, GL_FLOAT, 0, 0, vertices);
        gl.DrawArrays(GL_TRIANGLES, 0, 3);
        if (!eglSwapBuffers(display, surface)) {
            ok = 0;
            break;
        }
        ++submitted;
    }

    printf("ALR_GLES_PROC_DEMO_WORKLOAD requested=%d submitted=%d\n", frames, submitted);
    ok &= require_step(submitted == frames, "frameLoop");
    ok &= require_step(eglDestroyContext(display, context), "eglDestroyContext");
    ok &= require_step(eglTerminate(display), "eglTerminate");
    return ok ? 0 : 6;
}
