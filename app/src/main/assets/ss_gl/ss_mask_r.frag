precision mediump float;
uniform sampler2D _MainTex;
uniform sampler2D maskbefore;
uniform float ratio;
varying vec2 ft_Position;

void main() {

    float valnew = texture2D(_MainTex, ft_Position).b;
    float valbefore = texture2D(maskbefore, ft_Position).r;
    gl_FragColor = vec4(mix(valbefore, valnew, ratio), 0.0, 0.0, 0.0);
}
