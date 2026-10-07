precision highp float;
uniform sampler2D _MainTex;
uniform sampler2D skinhist;
varying vec2 ft_Position;

void main() {
    vec4 pixel = texture2D(_MainTex, ft_Position);
    float y = dot(vec3(0.299, 0.587, 0.114), pixel.rgb);
    float cr = ((pixel.r - y) * 0.713) + 0.5;
    float cb = ((pixel.b - y) * 0.564) + 0.5;
    vec4 res = vec4(0.0);
    vec2 cb_cr_256 = vec2(cb, cr) * 256.0;
    if (cb_cr_256.g >= 130.5 && cb_cr_256.g < 181.5
            && cb_cr_256.r >= 87.5 && cb_cr_256.r < 140.5) {

        res.r = texture2D(skinhist, (cb_cr_256 - vec2(87.0, 130.0)) * vec2(0.018867924528, 0.019607843137)).r;
    }
    gl_FragColor = res;
}
