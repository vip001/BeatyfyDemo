precision highp float;

varying highp vec2 uv0;
varying lowp float x_coor[12];
varying lowp float y_coor[12];

uniform sampler2D _MainTex;
#define ori_img _MainTex

uniform float value_factor;
const vec2 unit_uv = vec2(1.0 / 720.0, 1.0 / 1280.0);

vec4 processing_1pixel(vec4 sum_value, float center_gray, vec2 coor) {
    vec3 tmp_color = texture2D(ori_img, coor).rgb;
    highp float tmp_value_dis = tmp_color.g - center_gray;
    tmp_value_dis = 1.0 - min(tmp_value_dis * tmp_value_dis * value_factor, 1.0);
    sum_value.xyz += tmp_color * tmp_value_dis;
    sum_value.w += tmp_value_dis;
    return sum_value;
}

void main(void)
{
    vec3 center_color = texture2D(ori_img, uv0).rgb;
    vec3 res_color;

    highp float center_gray = center_color.g;
    vec2 tmp_uv;
    float num1;
    float num2;
    highp vec4 sum_value = vec4(0.0, 0.0, 0.0, 0.0);

    tmp_uv = vec2(x_coor[4], uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[5], uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, y_coor[4]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, y_coor[5]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[10], uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[11], uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, y_coor[10]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, y_coor[11]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[2], y_coor[0]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[2], y_coor[1]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[3], y_coor[0]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[3], y_coor[1]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[0], y_coor[2]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[0], y_coor[3]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[1], y_coor[2]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[1], y_coor[3]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[6], y_coor[8]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[6], y_coor[9]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[7], y_coor[8]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[7], y_coor[9]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[8], y_coor[6]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    tmp_uv = vec2(x_coor[8], y_coor[7]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[9], y_coor[6]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(x_coor[9], y_coor[7]);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    num1 = 12.;
    num2 = 16.;
    tmp_uv = vec2(uv0.x + unit_uv.x * ( num1), uv0.y + unit_uv.y * ( num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * ( num1), uv0.y + unit_uv.y * (-num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * (-num1), uv0.y + unit_uv.y * ( num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * (-num1), uv0.y + unit_uv.y * (-num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    num1 = 16.;
    num2 = 12.;
    tmp_uv = vec2(uv0.x + unit_uv.x * ( num1), uv0.y + unit_uv.y * ( num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * ( num1), uv0.y + unit_uv.y * (-num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * (-num1), uv0.y + unit_uv.y * ( num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * (-num1), uv0.y + unit_uv.y * (-num2));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    num1 = 20.;
    tmp_uv = vec2(uv0.x + unit_uv.x * ( num1), uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x + unit_uv.x * (-num1), uv0.y);
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, uv0.y + unit_uv.y * ( num1));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);
    tmp_uv = vec2(uv0.x, uv0.y + unit_uv.y * (-num1));
    sum_value = processing_1pixel(sum_value, center_gray, tmp_uv);

    sum_value.xyz += center_color;
    sum_value.w += 1.0;

    res_color =  sum_value.xyz / sum_value.w;

    gl_FragColor = vec4(res_color, 1.0);
}
