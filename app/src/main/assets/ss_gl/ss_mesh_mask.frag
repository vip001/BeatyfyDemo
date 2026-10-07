precision highp float;
uniform sampler2D faceMaskTexture;
varying vec2 vMaskUv;

void main() {
    lowp vec4 maskColor = texture2D(faceMaskTexture, vMaskUv);
    gl_FragColor = vec4(maskColor.rgba);
}
