precision mediump float;
precision highp int;

uniform sampler2D _MainTex;
uniform sampler2D faceSkinMaskTexture;

varying highp vec2 textureCoordinate;
varying highp vec4 textureShift_1;
varying highp vec4 textureShift_2;
varying highp vec4 textureShift_3;
varying highp vec4 textureShift_4;

void main() {
    vec4 cur = texture2D(_MainTex, textureCoordinate);
    if (texture2D(faceSkinMaskTexture, textureCoordinate).z > 0.004999999888241291046142578125) {
        vec4 n1 = texture2D(_MainTex, textureShift_1.xy);
        float w1 = 0.1500000059604644775390625 * (1.0 - min(distance(cur, n1) * 5.248638629913330078125, 1.0));
        vec4 n2 = texture2D(_MainTex, textureShift_1.zw);
        float w2 = 0.1500000059604644775390625 * (1.0 - min(distance(cur, n2) * 5.248638629913330078125, 1.0));
        vec4 n3 = texture2D(_MainTex, textureShift_2.xy);
        float w3 = 0.119999997317790985107421875 * (1.0 - min(distance(cur, n3) * 5.248638629913330078125, 1.0));
        vec4 n4 = texture2D(_MainTex, textureShift_2.zw);
        float w4 = 0.119999997317790985107421875 * (1.0 - min(distance(cur, n4) * 5.248638629913330078125, 1.0));
        vec4 n5 = texture2D(_MainTex, textureShift_3.xy);
        float d5 = 1.0 - min(distance(cur, n5) * 5.248638629913330078125, 1.0);
        float w5 = 0.0900000035762786865234375 * d5;
        vec4 n6 = texture2D(_MainTex, textureShift_3.zw);
        float w6 = 0.0900000035762786865234375 * (1.0 - min(distance(cur, n6) * 5.248638629913330078125, 1.0));
        float w7 = 0.0500000007450580596923828125 * d5;
        vec4 n8 = texture2D(_MainTex, textureShift_4.zw);
        float w8 = 0.0500000007450580596923828125 * (1.0 - min(distance(cur, n8) * 5.248638629913330078125, 1.0));
        float sumW = (((((((0.180000007152557373046875 + w1) + w2) + w3) + w4) + w5) + w6) + w7) + w8;
        vec4 sumC = ((((((((cur * 0.180000007152557373046875) + (n1 * w1)) + (n2 * w2)) + (n3 * w3)) + (n4 * w4)) + (n5 * w5)) + (n6 * w6)) + (n5 * w7)) + (n8 * w8);
        if (sumW < 0.4000000059604644775390625) {
            gl_FragColor = cur;
        } else if (sumW < 0.5) {
            gl_FragColor = mix(cur, sumC / vec4(sumW), vec4((sumW - 0.4000000059604644775390625) * 10.0));
        } else {
            gl_FragColor = sumC / vec4(sumW);
        }
    } else {
        gl_FragColor = cur;
    }
}
