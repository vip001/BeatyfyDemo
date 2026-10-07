precision mediump float;
precision highp int;

varying highp vec2 ft_Position;
#define uv ft_Position

uniform sampler2D _MainTex;
uniform sampler2D preImg;
uniform sampler2D blur1;
uniform sampler2D blur2;
uniform sampler2D face_mask;

uniform float smoothIntensity;

void main() {
    vec4 ori = texture2D(_MainTex, uv);
    vec4 pre = texture2D(preImg, uv);
    vec4 b1 = texture2D(blur1, uv);
    vec4 b2 = texture2D(blur2, uv);
    vec4 mask = texture2D(face_mask, uv);
    highp vec3 o = ori.xyz;
    vec3 s;
    if (smoothIntensity > 0.00999999977648258209228515625) {
        float s06 = step(0.60000002384185791015625, smoothIntensity);
        highp float mz = mask.z;
        float sf = mix(0.300000011920928955078125, smoothIntensity - 0.300000011920928955078125, s06);
        highp float lg = clamp((min(pre.x, b2.x - 0.100000001490116119384765625) - 0.20000000298023223876953125) * 4.0, 0.0, 1.0);
        vec3 even = mix(pre.xyz, clamp((b2 + ((((pre - b1) * vec4(0.5)) + vec4(0.5)) * 2.0)) - vec4(1.0), vec4(0.0), vec4(1.0)).xyz, vec3(mz));
        float amt = mix(mix(0.0, (0.5 * sf) * lg, step(0.5, lg)), sf * mz, step(0.004999999888241291046142578125, mz));
        even = mix(even, b2.xyz, amt);
        s = mix(o, even, mix(smoothIntensity * 1.66999995708465576171875, 1.0, s06));
    } else {
        s = o;
    }
    gl_FragColor = vec4(s, ori.w);
}
