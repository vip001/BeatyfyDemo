precision mediump float;

uniform sampler2D _MainTex;
uniform sampler2D faceSkinMaskTexture;
uniform sampler2D blurImageTexture;
uniform sampler2D lutImageTexture;

varying highp vec2 ft_Position;

vec4 LUT8x8(vec4 inColor, sampler2D lutImageTexture)
{
    highp float blueColor = inColor.b * 63.0;

    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);
    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);

    highp vec2 texPos1 = (quad1.xy * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * inColor.rg);
    highp vec2 texPos2 = (quad2.xy * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * inColor.rg);

    lowp vec4 newColor2_1 = texture2D(lutImageTexture, texPos1);
    lowp vec4 newColor2_2 = texture2D(lutImageTexture, texPos2);

    lowp vec4 newColor22 = mix(newColor2_1, newColor2_2, fract(blueColor));

    return newColor22;
}

void main()
{
    lowp float dark_threshold_Factor = 28.86751;

    mediump vec4 srcColor = texture2D(_MainTex, ft_Position);
    mediump vec4 maskColor = texture2D(faceSkinMaskTexture, ft_Position).rgba;

    lowp float blendFactor = maskColor.b;

    mediump vec4 dstColor = srcColor;
    if (blendFactor > 0.005) {
        lowp vec4 blurColor = texture2D(blurImageTexture, ft_Position);
        lowp float cDistance = distance(vec3(0.0, 0.0, 0.0), max(blurColor.rgb - srcColor.rgb, 0.0))
                * dark_threshold_Factor;

        if (cDistance > 0.5 && cDistance < 5.0) {
            mediump vec4 lutColor = LUT8x8(srcColor, lutImageTexture);
            dstColor = vec4(mix(srcColor.rgb, lutColor.rgb, blendFactor * 0.4), 1.0);
        }
    }

    gl_FragColor = dstColor;
}
