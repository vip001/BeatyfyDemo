attribute vec4 v_Position;
attribute vec2 f_Position;
varying vec2 vMaskUv;
uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

void main() {
    gl_Position = uMVPMatrix * vec4(v_Position.xy, 0.0, 1.0);
    vMaskUv = (uSTMatrix * vec4(f_Position.xy, 0.0, 1.0)).xy;
    gl_Position.y = -gl_Position.y;
}
