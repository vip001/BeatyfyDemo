precision mediump float;
uniform sampler2D _MainTex;
uniform vec2 jumpstep;
varying vec2 ft_Position;

void main() {
    vec2 uv = ft_Position;
    float res = 0.0;
    res += texture2D(_MainTex, uv + jumpstep * (-5.5)).r;
    res += texture2D(_MainTex, uv + jumpstep * (-3.5)).r;
    res += texture2D(_MainTex, uv + jumpstep * (-1.5)).r;
    res += texture2D(_MainTex, uv + jumpstep * 0.5).r;
    res += texture2D(_MainTex, uv + jumpstep * 2.5).r;
    res += texture2D(_MainTex, uv + jumpstep * 4.5).r;
    res /= 6.0;
    gl_FragColor = vec4(res, 0.0, 0.0, 0.0);
}
