attribute vec4 v_Position;
attribute vec2 f_Position;
varying vec2 ft_Position;
#define uv ft_Position

void main() {
    gl_Position = v_Position;
    uv = f_Position;
}
