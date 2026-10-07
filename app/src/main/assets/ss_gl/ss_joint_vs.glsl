precision highp float;
attribute vec4 v_Position;
attribute vec2 f_Position;
varying highp vec2 uv0;
varying lowp float x_coor[12];
varying lowp float y_coor[12];

void main()
{
    gl_Position = v_Position;
    uv0 = f_Position;

    vec2 unit_uv = vec2(1. / 720., 1. / 720. / 16. * 9.);
    int i, j;
    float nums[2];
    lowp float tmp_ = 0.6;
    nums[0] = 1.0 * tmp_;
    nums[1] = 2.0 * tmp_;

    float num;
    i = 0;

    for (j = 0; j < 2; j++)
    {
        num = 3. * nums[j];
        x_coor[i] = uv0.x + unit_uv.x * (num);
        y_coor[i] = uv0.y + unit_uv.y * (num);
        i++;

        x_coor[i] = uv0.x + unit_uv.x * (-num);
        y_coor[i] = uv0.y + unit_uv.y * (-num);
        i++;

        num = 4. * nums[j];
        x_coor[i] = uv0.x + unit_uv.x * (num);
        y_coor[i] = uv0.y + unit_uv.y * (num);
        i++;

        x_coor[i] = uv0.x + unit_uv.x * (-num);
        y_coor[i] = uv0.y + unit_uv.y * (-num);
        i++;

        num = 5. * nums[j];
        x_coor[i] = uv0.x + unit_uv.x * (num);
        y_coor[i] = uv0.y + unit_uv.y * (num);
        i++;

        x_coor[i] = uv0.x + unit_uv.x * (-num);
        y_coor[i] = uv0.y + unit_uv.y * (-num);
        i++;
    }
}
