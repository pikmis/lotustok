package cat.narezany.margyt;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.TextureView;

import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

/**
 * The LotusTok mark as a model that can be turned: the .glb it was made as (every piece of it,
 * with its thickness) kept in this file in a packed form, and drawn with OpenGL into a view that
 * is a view like any other, so it scrolls with the page it is on.
 *
 * It is dragged to turn, keeps turning for a moment after the finger leaves, and then settles
 * back facing front, with a slight sway so that it is seen to be solid. It is drawn in the
 * colours of the file, with no light on it.
 */
final class Logo3D extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String[] DATA = {
        ""
                + "eNrsvWlYFFe3NrwaZVSbEgVFoJEq0RJFUUGKSYlDi/OcoFGTOBInxFljfPJoHHHAMagxcYoxxjHOrSYqg61oHDCxo10OLQ4x0VQn"
                + "ThCl3nvV85xzPNf1+b0/3h/n/MC69l1Vq+5qdletfe9h9d7WpPDwcNM4nw3ZJ0RlWu1styRP+WPhRVGZKmf/LN6eeiq7tZg39evs"
                + "ejg2ZwfBfvnIHmbaeoH56MhN0TX15CkV9pl5H8LSPc8LlsiiO6Iyed+5fpI8aeY5T9E1Of1KrqhM+P5KmSSPn33lgOiaMNjpEJXM"
                + "HWqkJI8d6fxDdGV2K5kpKiNzSg5K8ofWkiWia2TMbxmiMvST37IleUjT38aLrqFh7qmiMnC1e64kD2jlniG6BjZ7tlJU+mrPFkly"
                + "n5XPlouuvgvLtopK1z1l4HTpXbZedHXt9GqdqLT7+9VQSW678VW26Gq3Sh8uKklP9RBJTtyq9xBdSSuIRKW5Xd8iyc2W6ndFV/PR"
                + "bCFNF4CkfyHJNFi34diqHxfzqK0+RrxDFt0FXHGpLnMuW8EZeGmy6KKQ03/BsvZMS1ju23vCsvf7R6LiMexEqiR7rP8Bf9Fj8kFF"
                + "VDz7H8qRZM+PDiKfnkN3vhAV39G7e0iy76RdA0WX79itvUTFPP/rryXZPH3rQdFl/vRLPJOauRsOSXLNiV+eFV01F69JF5WQWWu/"
                + "lOSQtDWHRVdI1spkUZHkVTMkWfxl5RrRJVXPiRKVRhnLBktyo8o5c0VXo86L40QlNmzJcElu8dXipaKrRVn2SFFJkRatlOTkVdkn"
                + "RVdyKXuCtUf2E0luvzO7vuiy1i3E30rWCpCHZMrvIMmtggt24Xh7wWAxL/lFQQ3xdvLzghHi7ZQOv+A9tgq5ZvCvNRRdKR3Ue5Kc"
                + "cu1GFizf3ugIS2VXJVgCXNPwLm643sZfVO7+LclJp+8NFJXERfdSRVfig19/kOSEVw+riopy9Nenoish+FErSY4/9miyqLSc8Gi4"
                + "6GpZrI2U5DiztlhUWhzXZouu2Lp/xeE9lv/VVlRitv7VWnQ1q/IU36tJ7tNsUYlOfrpYdEVveo7nE/Xw+QBRabj4+TDR1fCvFyWS"
                + "3KDSC3yjyG9efC+66ptLT+MZuks/EpWIjaXLRJdYuSxUki0nS1FewuaXXhddYSV87B1TJuI93niBJ+aVWqqKd7z6lO4Q8wy84yWV"
                + "Tod97p3t4NwpMTgl00WXV+TNCZLsFX/rECxzbqXDMvb6QEn2ruPcjc/s5ewiurzbXAXHZ7ajSFR8tjjwfX3WFudLst/on0JFxW/S"
                + "lUOiy2/8RdxV9dyl70Sl6vFL3URX1VPnu0iy/2c/wvP9J/yYILr8Z56FDwfkF90VlYCFRXNEV8A6O+4KantmvagEPjmDpxRUpRBP"
                + "qY736XOiErz5dJboCj5R0FGSw/RC5D9sXmEEvu/2/Nl4JqEFO/FMlhbgrUUczm8nyfU7s1dEbisIEF2RjuwTpE2rvfAiaVASN9GU"
                + "P7KDcHw5uzUNYz2hDtCTn4GnbL1w9dGRPcw/cpOsU0/mfQhL91Mq+DPzvGCJPNePaNLMojukQVU8yQo9KSOCkuSSBlU5QNYJg9VI"
                + "IiiJg7TMHc4/yAo9OUgEJZlJGlRlCVmhJ9lEUJIM0qAq48kKPZlLBCWZShpUZQZZoSeLiKAkK0mDqiwnK/QEHCjJVtKgKuvJCj0Z"
                + "SgQlWUcaVCWbrNCTECIoyXDSoCo9yJq0Qt9C1GwpEWlQlbtkZT0hDXryBfaDdQHHpLuoPa3Qj9Mw1hMcW3Qb7NbLVnAGXqrLnEuT"
                + "yUohZ1rCcv/0X7CstfeEZe+JVCKP9d8/Is1j2A/4ix6TD+UQQUmgWZ79DyKfnkN39yDynbTzBWm+o3cNJKvv2K+/JoKS9CLNPH/r"
                + "QbKaP91wiAhKgmdSM/fLs2StuXjtl0RQknTSQmatOUzWkKxVM6CFv6xMJk2SV64hq1R92WAiKEkUaY0ycuaStVHnJcOJoCRxpMWG"
                + "LV5K1hZli1YSQUlGkpYiZZ8ka3Jp9hOi9jvZK6Aq9cnKekJaspbfgQhKgjwkU8EI6pDSoWAwDWM9oQ7Qk12wb79mMH/BO4WqNCRr"
                + "SocbWbB8q94jgqp0hKWyaxqe/w1XJVgCXG/jLyr3BpKWuOju30RQlVSyJj54WJU0KMkPRAmvfn1KVujJZNKgJK2IoCrDyQo9WUwa"
                + "lGQkEVRlNlmhJ21Jg5LE4Z2W/9WarNCTbNKgJPiOUJXFZIWeDCANSoJnBVUZRlboCXIOJSkhgqp8T1boyUekQUlO43m6S5eRVaxc"
                + "ipISNr8slAiqcp2srCekece8wFPySi0T8U5vlE7H8dzSHTSM9YTaQ09UYJ8Sg3NnOzh3SqaT1Svy1iFY5tycQARVSYdlrHM3Pq3X"
                + "9YFEUJUuZPVu4ygizWfLVXCgKvi+Pmt/CiXNb1JxPpHf6CuHyOo3/tJ3pFU9fhF3QVW6kbXqqR/h7f4TznchgqokkNV/ZtFd0gIW"
                + "noU/Q1XmkDVg3Zn1pAU+seMuqAqeUlCV0+dIC95ciKcEVckia/CJQnyXsHkFHYmgKhH4vtsLduKZLM2fjWcSWoC3FnGYPSFyW347"
                + "IqhKAFkjHfD+SlQZWJm8yESe2JuQKpEHzisDK8PWhJpRc2BziqWm1AL7pkjNKAbnzYHNYRtA79H7wPdpMA2kD7AfiPQeDcL5+8D3"
                + "YVtIi2kJcAnlUDYtxT4baTEtwvkS4BLYDtNROgY8Rt/TETqO/RGko2TD+THgMdju0a/0EPiQfqf79Bv295F+pQc4fwh8SL+Zappq"
                + "mWoDa5vqmAJNwdgHItUyBeG8NrA2bG1M7U1WoNWUZmpr6oB9W6T2pnY4twKtJpQf8iZfoC+eSSWgD9AHz2govutgIMoTtsH4hsOQ"
                + "hoDtR1WBVcH1BVYBVsEdI8AdBhxGGdiGgZmBNBzsauQP9Ae3KtAMNOOOkeBmADNoFLYMMEchfQi2QAHAAHD9gdWB1XHHGHBHAUfR"
                + "WGyjwByLNBrsGhQIDAQ3AFgTWBN3jAN3LHAsZWEbC2YWUibYQVQbWBvcQGAtYC3cMQHcLGAWTcSWBeZEpPFgB1MIMATc2sA6wDq4"
                + "YzK4E4ETaQq2iWBOQZoEdihZgBZwQ4BhwDDcMQ3cKcApNB3bFDCnI00FO5wigBHgWoB1gXVxxwxwpwOn08fYpoP5MdJHYItUD1gP"
                + "3AigBJRwxz/A/Rj4MX2C7WMwP0GaCXYkNQA2ALcesD6wPu6YBe4nwE9oNrZPwJyN9E+wZYoCRoHbANgQ2BB3zAF3NnA2zcU2G8y5"
                + "SJ+C3YiigdHgRgEbAxvjjvngzgXOpQXY5oK5AGke2E1QqhohRWOLRemKRmqMUjMfjIVIi7EtAHMxUjZKYhzFA+NRBlsAWwJb4o7l"
                + "KE9LgUtpBbalYK5AWga2QonARHDjgQnABNyxCtwVwBW0GtsKMFcjrQQ7iVKAKeAmApOBybgjF9zVwNW0BttqMNcgfQZ2K0oFpoKb"
                + "AmwNbI071oG7BriGPse2BszPkdaC/Ra1BbYFNxXYBtgGd3wB7ufAz+lLbJ+D+SXSerDboZZ+C6ktNivq9rZIbWgjuF8Cv6RN2L4E"
                + "cxPSBrA7UEcg6jFsHSkNmIY7toC7CbiJvsK2CcyvkDaD3Ym6ALuA2xHYGdgZd3wN7lfAr2gbtq/A3Ia0Feyu1B3YHdwuwG7Abrhj"
                + "O7jbgNvoW2zbwPwW6Ruwe1AvYC9wuwN7Anvijp3gfgv8lnZh+xbMXUg7wO5NfYF9we0F7APsgzv2gLsLuIv2YtsF5l6k3WC/TenA"
                + "dHD7At8BvoM79oG7F7iX9mPbC+Z+pO/A7kfvAt8FNx3YH9gfdxwEdz9wPx3Cth/MQ0gHwPZAjdAP6V1snlDCd5H6Q+0PgjEQ6QNs"
                + "h8D8AGkQFPsHOgk8Cf3+HngCeIIOm0Khx7WAtUxh2GpBqcOQQsA+RfnAfHBPAvOAeXTCFA5uGDDMVBdbmCkEGGKygF1Ap4Gnwc0H"
                + "FgILKc8kglsXWNckYatrsgAtpgiw7XQWeBbc08AzwDNUaIoEVwJKpvrYJFMEMMJUD+wiOg88D+5Z4DngOTpjksGtD6xvaoitvqke"
                + "sJ6pAdg/0kXgRXDPAy8AL9A5UyNwGwIbmhpja2hqAGxgigL7EhUDi8G9CLwMvEwXTE3AbQxsbGqKrbEpChhligb7Cv0M/BncYuBP"
                + "wJ/osqkZuE2BTU3NsTU1RQOjTTFgX6VfgL+A+zPQAXTQT6ZYcJsDm5visDU3xQBjTC3AvkZOoBPcX4DXgdfJYYoHNw4YZ1KwxZla"
                + "AFuYWoKt0k3gTXCdwBvAG3TdlAiuAlRMSdgUU0tgS1MC2LfIBXSBexN4G3ibbphSwE0CJplaYUsyJQATTMlg36G7wLvguoAlwBK6"
                + "bUoFtxWwlektbK1MycBkU2uw76MlcAfpLrZf0Uq4i1SC2jwVjHZIadjeMrUGtja1AfsR/QH8A+2I34CPgY/pgakTuB2AHUydsXVA"
                + "y6AzUkewNfoT+Ce4fwDdQDc9NnUFtzOws6kbts6mjsCOpi5g/0VPgU/B/RP4BPiE3KYe4HYDdjP1xNbN1AXYxdQd7Gf0AvgC3KfA"
                + "58Dn9MTUG9yewJ6mPth6mroDu5t6gV1KfwP/BvcFsAxYRs9Nb4PbB9jH9A62PqZewF6mvmC/pHJgObh/A18BX1GZqR+47wDfMfXH"
                + "9o6pL7CvKR1s3WSil0jlVG7CDkj0yjQA3P7A/qaB2Pqb0oHppnfB9jBVBlY28b/KpkrASiYyvQfuQOBA0/vYBpreBb5rGgS2p8kb"
                + "6A1uZaAX0At3DAb3feD7piHY3gdzCNIHYPuY/IB+4HoDfYG+uGMYuEOAQ0zDsQ0BczjSULCrmKoBq4HrB6wKrIo7MsAdDhxu+hDb"
                + "cDA/RBoBttkkAAVwqwH9gf64YxS4HwI/NI3G9iGYo5FGgl3dVANYA1wBGAAMwB1jwR0NHG3KxDYazEykMabq0MLjwOPg1kBb9gjw"
                + "CO4IBDcTmIl2aTBwDHCMKQhqewStXw8kL2xH0TL2QkLnCF79O/B3tMObAR8AH6AdMAAqWgdYB23v99DWDQIGQZcXwXvbA9ujrZ2D"
                + "Nm4bYBu0J/pDvQcBB0GtTWinDgEOwZkH9TMFkAfSEbRmCX/TG+kEGFXQDvVBGoJWqzcsfkh5sJrR3qyCNBytUz9YqiEVwlod7Uoz"
                + "0odohVaDRUA6A2tNtB+rI41Ga1OApQbSOVhroZ1YEykTrcoasAQhXYC1DtqDtZDGo/UYBEsw0mVYw9Duq4M0Ca3EYFhCkX6CtS7a"
                + "d2FIU9EaDIUlHMkBq4R2XF2kj9DqC4dFRLoOa3201ySkmWjdibBEIt2AtSHaZfWR/olWXCQsMtJtWBuj/dUQ6VO01mRYGiGVwBqD"
                + "dlZjpHl4O41gaYJ0D9aWaE/FIGWj9dUU7yoO6TGsCWg3tURahlZWHCwKkhvWZLSPEpBWojWlwJKE9ATW1mgHJSN9hlZTEiytkJ7D"
                + "2gbtndZIa9E6agXLW0hlsLZHu6YN0nq0gt6CpR3SK1jT0H5pj7QBrZ12sHRAqSZYO6Odkoa0Ga2aDrB0QumtBGs3tEc6I21F66UT"
                + "LF1RSr1g7Yl2Rzekb9BK6QpLD5RGX1j7oH3RE2kHWiM9YOmNUlcV1nfQjuiDtButjt6wvI3S5Q9rf3jgO0jfoXXxNiz9UIoC0KoY"
                + "CP89iB7bGPS4hqKFMAD1fk30pkag9h+KOj0EPaWRqNlHoL62oBc0BrX2SNTFEejhjEONPAb1bD30Xiagth2HOrQBeiaTUZNOQP0Y"
                + "hV7HNNSSk1H3RaNHMQM14DTUazHoLfwDtdsM1Fkt0BOYhZrrH6iPWqKVPwe10izUNQlowc9HjTMH9UgyWucLUZ7mozfZGi3v5agp"
                + "FkH/26JVvQq1wHJoe0e0mHOh8Kug213QGl4H9c6FJndHS/cLKPM66G0vtGI3QnW/gJb2RQt1CxR1I3QyHa3Pr02DcD4Ilq9pu+kD"
                + "nH8Ay3baaRqK86Gw7KQ9phE4HwHLHtpnGonzkbDsw/Mbg/MxOKsOnQkEBkKHbDgKBYaivfUD2lfhwHC0pU6h7SQCRbSTCtAuigRG"
                + "og1kR5tHBspo3xShPdMI2Ahtlx/RVmkCbIJ2ySW0Q5oBm6HNcQVtjFhgLNoTV9F+iAfGo61wDW2DRGAi2gEq6v0UYArq+Fuo01OB"
                + "qai/76C+bgdsh7r5PuriTsBOqHcfoZ7tCuzKY36oQ3sAe6C+/Av1Y29gb9SFz1D3vQ18G/VcKeq1fsB+qMleos4aABxAOmqoAab3"
                + "gO+h7vEADgYORr3iCRwGHIY6wweYAcxAfVAFOAo4ClpvBo4FjoWO/zvOMzFVtU+rPdHf6Zjyx6QY1T5Vnhilhk89NfGkmjL164lO"
                + "HJsnPoT9srUHM9vvAvORNUK1TD2ZIsE+M/kzWLonl8ESGRum2ifva/G10zFpZou/Vcvk9EYZqn3C9428nI7xsxt1Vi0TBkuyas/c"
                + "IV53OsaOlKqrlsxuIbNU+8ickE5Ox4fWkPGqZWRMzVzVPvSTmpOcjiFNay5RLUPDzPNV+8DV5ulOx4BW5k9Vy8BmvqNVe1/Nd6LT"
                + "0Wel71jV0nehZ3/V3nWPJzhdensOUS1dO3kMU+3t/vb43Olou9Fjkmppt4rWqvakp3TX6UjcSntUC8d5VHtzOw1wOpotpRDVwuOy"
                + "qp3fEpBosNNBg8mKY/Tc1BT02laoYdynB65ocos50TZwBjZZqFooRKkGy9qWZ2G5H78blr1v1VDtHsNan3A6PNan4i96TO5oV+2e"
                + "/dPGOR2eH3VEPj2H9vJR7b6je+xxOnwn9dysWnzH9tul2s3z0/s5Hebp/TqpFvOn7+OZ1Mx9r6PTUXPi+y1VS83Fw7ep9pBZwz5w"
                + "OkLShqeplpCsUfmqXZJHfup0iL+MGqFapOqZP6v2Rhljv3A6GlXOnK5aGnUeX6TaY8Oy1jodLb4an6VaWpRNXK3aU6QJo52O5FUT"
                + "W6uW5FL2BGuPiVWdjvY7J15TLda6CfhbyVoi8pBMSUecjlbBib1wvD3xCzUl+UXiIzU8+XniGjU8pUN9vMdWIZEGP/KqaknpINZx"
                + "OlKuRSyF5duIQ7BUDnsFS0DYPLyLG2Hb8ReVOp5OR9Lp4M2qPXFR8AnVkvgg6C2nI+FV4BPVrhwNqqJaEoIDTjkd8ccCFqr2lhMC"
                + "1qqWlsX+q52OOLP/BNXe4rj/x6oltm7VIrzH8qrHVXvM1qonVUuzKn74Xk1y/Sap9uhkvwmqJXqTD55P1EOfLaq94WKfdaql4V/e"
                + "oU5Hg0re+EaR33i3US31zV4JeIZurzmqPWKjV6ZqESt7ljgdlpNeKC9h870iVUtYCR97x3jewHu84Y0n5pXqJalhXn28+qgpBoZ5"
                + "SV5zYZ8b+jY4d0IMTshc1eIVWXex0+EVH94Rljnh22AZW2+z0+FdR+qJz+wl7Vct3m1kcHxmN4hT7T5bGuD7+qxtnOx0+I2OKlHt"
                + "fpMadVQtfuOb4q6q55p0U+1Vjzf5TrVUPdV8v9Ph/1kzeL7/hGanVYv/zDj4cEB+bIhqD1gY+5FqCVgXj7uC2rYcotoDn7TEUwqq"
                + "koCnVMdbiVXtwZuVpaol+ETiIacjTE9A/sPmJdzE992e9DGeSWhibzyTpYl4axGHk445HfU7s1dEbkt8rFoiHRNTdWFa7UkxugAl"
                + "8ec4z8SHOL488aS+jvVEPwI9iQKear+L4zzWHsy3Rui2qSeTP+M4T4oE/szkMlgiW3zNcZ7YMF2Aqvyt26AnXhznaZShC1CVzrpt"
                + "wmDxOsd5JFkXMndI1XUb9KQTx3lCZukCVGW8boOeTOI4T81cXYCqLNFt0JPpHOcxz9cFqMqnug16MpHjPL6jdQGqMla3QU+mc5zH"
                + "s78uQFWG6Dboyecc5/EYpgtQlUm6DXpyl+M8tFYXoCp7dBv0ZMC/4jy6AFUJ0W2sJ0Z0ZzCrCcePsbfoR2kFtdXXsZ7g2EJW2K3R"
                + "No7zNLnFnCYLdRuFtDzLcR6lGixr43fDsrf1CY7zvFVDFzyGpeIvekxOG8dxno52XfDs3xH59BzaYw/HeXr56ILv6J6bdZvv2PR+"
                + "HOfpt0sXzPP7ddJt5k/f68hxnvfxTGrmvt9St9VcPOwDjvMM36YLIbOGp+m2kKyRn3KcZ1S+LkjyqBG6Tao+9guO82T+rAuNMjKn"
                + "67ZGnbPWcpxnfJEuxIaNz9JtLcomjOY4z8TVupAiTWyt25JLJ1blOA97BVTlmm5jPdGFZC3pCMd5EpGHZEpcox9J6ZD4hb6O9UQ/"
                + "Aj3pBfv2SINZvxPHeSKv6raUDhFLYflWrMNxnohDsFQOm4fnfyPsFcd5wrbjLyrBm3UhcVEdT47zBJ/QbYkPAp/oApTkLY7zBFXR"
                + "bdCThboAJTnFcZ6AtboNejJBF6AkqznO4/+xboOeHNcFKEkRx3mqntRt0JNJugAlGc1xHr8Jug16skUXoCSfcpzHZ51ug54g51CS"
                + "UI7zeLfRbdCTOboAJUngOI9Xpm4TK3uhpITN9yzhOI9XpG5jPdEF7xhvPCWvVM8bHOfxmovjuV599HWsJ/pR6IkE7BNicELf5jhP"
                + "yFzd5hUZ3hGWOXUXc5wnfBssY6We+LRe9TZznEfar9u82zSI0wWfLfJijvM0wPf1WRtVogt+kxonc5ynUUfd5je+STddqHq86WaO"
                + "8zT5TrdVPdUM3u4/ofl+jvM0O63b/GfGhuhCwMK4wRznif1ItwWsazlEFwKfxG/mOE9LPKWgKkqsLgRvThjNcR5lqW4LPpGA7xI2"
                + "L/EQx3kSbuL7bk/sjWeyNOljjvMk4q1FHGZPiNyWdIzjPImPdVtFnKcizlMR56mI81TEeSriPBVxnoo4T0WcpyLOUxHnqYjzVMR5"
                + "KuI8FXGeijhPRZzn/xbnMf3999+kmNoVZJiFZO1aOuONLOC3rmlmIenGvYFmIXHRw6pmQTn6aLJZaDlBW2wWWhz/q61ZiNn6NNss"
                + "RCc/H2AWGi5+scssRH5T+pFZiNhYesIshM1n9Eotuch46xBwjnO3WfDu5SgyCz5bfgo1C36TLn1nFqoe/3GdWfCfUHTXLAQsPLPe"
                + "LAQ+OX3OLARvLpyOz5lXsBOfuZRzGLmtIIOI88l4IwuIfBJxPok4n0ScTyLOJxHnk4jzScT5JOJ8EnE+iTifRJxPIs4n461DQOST"
                + "iPNJxPkk4nwScT6JOJ9EnE8izicR5xOfg3ziM5dyDiO3UQjqHAgP6qFKxgiVJzYv1DfeqHt4ZMUPdU0VqooapRpqFx4LEVCbVKcA"
                + "1Bk1UH/w6EUQ6otaVBu1QjBqCB5vMBmf5ml8jq/xCdWMe6sbdwUa/GCDWcngVDGu1jDsIYbFH0dhqE/ijb52HLZYo+fd3KiHY6Ds"
                + "TaH40UZPvxE2HiFoiPqhgTG2EImNxyQk1DARxmhGODYeBQnD5/GntTA+p6nxCY2MexsYd0kGP9xgxhmcaONqpGEPMywNcUTIZzww"
                + "HnmEeBsYiuse2DNybI1H9xgt+Dwe62MMR1549I+xLj7HC3vGCOTUG3tGEbnwwZ5Rwvfg0TDGesiFH/aMkfiWVbBnrI+88wgYYwM8"
                + "g2rYM8rIqRl7xoZ4QjzqxRiFbyxgz9gIz6869oyN8V15pIsxGk+3BvaMTfCcamLP2BTPnke3GGPwLIKwZ+SRy1rYM/I4JY9oMbbA"
                + "ewvGnjEWT7QO9oxxeKs8isXYkuL/o3wn5rq1ZC1yG2PEUuC3YfPcWtKN4M1uLXFR4BO3phwNWOjWWk7wn+DWWhyvetytxWz1m+TW"
                + "opN9tri1hou9e7m1yG+85ri1iI1eqW4tbD6jV2pIDGN4R+Acqadb8+7VIM6t+WyJKnFrfpOadHNrVY83G+bW/CfEhri1gIUth7i1"
                + "wCdKrFsL3pwwF58zL7E3PnMp5zByW2Iul2yUH+J8cvkOm8flO3gzl+/AJ1y+AxZy+fafwOW76nEu336TuHz7bOHy7d2Ly7fXHC7f"
                + "Xqlcvhk5n4zhHbl8Sz25fDeI4/IdVcLlu0k3Lt/NhnH5jg3h8t1yCJdvJZbLd8JcLt+Jvbl8cw4ryndF+f7fUb75dxp0uHK7ZI1n"
                + "WCaTJDPyHMrk7aILx9liXnI2LK7k7bAoydvr5TKnXg5z6i1ne/hDSU7aG34Wlg3hxbD0D0mX5MQzIcGikrQ3pLHoShoT9FiSEzoG"
                + "LReVRDlop+hKuBdwQpLjEwPSREXxCxgluuLPm29JcuxmczdRiRtsnia64qKqvC/JzXL8DohK8yS/+6Krua9PV0luIngvEZUmm7wL"
                + "RVeTKZ4HJTkqpvI9UYna5imJrqg5lb6W5PoeHnZRqT+okp/oqt/Wo4EkR+SaWotKxFXTbNEVkW9qLclhaKuLSlh9Wi26wsJ4rlnt"
                + "dJ6DW/t9soqu2oP+NR+X7Yw845btOB4m5tEwWFw8psozdGunM6f2+8ypPYjtYV6SbGodVh+WHmFhsKyOwHPzaBBxVVRMrSPyRZdp"
                + "dn0PSa70df1BouJhr99WdFXyi4qRZM+DUdtEpfK9qDmiy1NqIkiyT9cmm0TFe0mTKaLLu7BZjiRXeb95kqj4HWjuK7r87sdulmTz"
                + "rbjBomLuFhcluszT4hMlOeCE4icqAWnx50VXwKiEjpIc9DhRFpWg5Qn3RFfQzsQzkhySnrRXVEKCk8aIrpDGSXslOfxh8gZRCT+b"
                + "3F90hRezV9TLYa+ol8v+UG95ssZz5pKJ58YBcZS8nWfLJWfTMPYTsrKfAOEhzKnHekz1lrM9/CwsG8IfQo/3hhfD0j8kmDR4CJ46"
                + "vKUxWeEny0mDhzwmgrfsJCv8JI00eAjaWPCWUWSFn3QjDR5yiwjeMo2scVF+B0hrnlTlfSJ4y32yNvf1XkJak00+XYngLYVkbTKl"
                + "8j3S4CEHiaJiPCWyRs3xsJMGD/maqL5HJT+y1m+LvosWcdWjAXQ61zSbrBH56NNpYfVhJ3jLarzXMJ5VCQ/R8KbT+f2zn7B/GBv9"
                + "O1mNNMzYNGPknbf/uo8/YxDb4H0aPMQLlUFrfLYVfnIVLfrWEXhu8JZ8ssJPBpEGD/Eggre0JSv8ZBtp8BDUSPCWOWSFn2wiDR4i"
                + "oEbq2mQKWb0LmyeR5negWQ4RvMWXrH734waTZu4Wi5oQ3hJFVvM0xY80eEgi6qsT8efJGjAqEX4MD0EtF/Q44R5Zg3Ym7SUtJDjx"
                + "DIQ3PWkMWUMaJ28gLfws7ARv6U9W+Am+EDwETwDegreP9/2/La7a36gb+yPxuH0l5OxdpH6wck1pQuIamBXfE8njjXHMN8Ur3xSX"
                + "fFP88U1xxjfFE98UN3xTfPBNccA3xfveFNd7U/yuEd5VM2AzoyZvZrQJmuDKm+Jxb4q7vSm+9qY42pviZW+Ki70p/vWmONeb4llv"
                + "ilu9KT71pjjU2/DB1+NN/YD9jHgTx9MPInG8iePph4wxyYE0BBoyEInjSsNoKHCoEWkfbkTQMwyNyTAi6xxj5xj5KCNanmHEzkca"
                + "UfQRRix8rBEVH2XEyMcY0fKRRsw7y4h+jzVi4eOMqPgYI7Y90YhyZxkx7wlG9HucEcOeYkSzJxqx7clGlHuCEauebkStpxgx7GlG"
                + "NHuyEZP+2IhOTzdi1TOMqPU0I/b8iRGF/tiISf/DiE7PMGLMs41o8ydG7HmWEYX+hxFLnmtElWcbMeY5RrR5lhEzXmBEj+caseT5"
                + "RlR5DqwLUfrnIXF8OQcqsABpvhEDXmFEg5caseHlRpR4kRHrXW1EfVcYMeBVRjR4uRHTXWNEd1cbsd5cI+q7yojdfm5EcdcYMd11"
                + "RnQ314jRfmlEaz83YrdfGFHcdUYsdpMRlf3SiNFuNKK1Xxgx16+M6OsmIxa7xYjKbjRiq9uMKOtXRsz1ayP6usWIoX5rRFO3GbHV"
                + "7UaU9WsjVrrLiJp+a8RQdxrR1O1GTHSvER3dZcRK9xhR051G7HO/EQXda8RE9xnR0T2wHjQinYeMOCnHMfcj7YMiDoTqeiDxOPx7"
                + "8FYv/iWaMdq7GLgYmh0LXAhcCCXoh2sDgAPwed5GhLQ/j3fyWChKUVceWzXGXd8yRmuTjDHeOGNkuJExnhxpjEKHG2PXwcaIdw1j"
                + "nLyaMbrOPZX+xqd1NT7nLeMT4ox7I427gg1+NYPZ2+AkGVfDDbu3YWmEIw9jzN8DaeB/G9t/fTz/9TH818ftXx+rf318/vUx+dfH"
                + "4V8fe399vP31MfbXx9Ub4zkuNH77MO+/jZ+/Pmb++jj562Pjr4+Hvz4G/vq49+tj3a+Pb78+pv36OPbrY9fv4N0eNHTrOyPOPZRH"
                + "pKEnI3nUmUebUfYn84gyjySjnM7i0WJsi1CmlvNIMI8Aw/+/4FFeHt2Fr27nEVweucUnHsDn8aeNMz5nhvEJ8417c427thj8PQZz"
                + "pMGZZVz9wrAfMCzLcfTv/k2ewL8szieng5F/O5z3tmpJprxJakpyNiyW5O2w2JO3X89gzvVxzLk+lu23gpyOpL23WsKy4VY0LP1L"
                + "tjkdiWdK7qv2pL0lV1RL0phfA5yOhI6/jlXtifKvvVVLwr1HqU5HfOKjw6pd8Xu0SrXEn3fXdTpiN7u/U+1xg93zVEtc1JMNTkez"
                + "nKedVXvzpKfBqqW57/N9TkcT4cV41d5k04tE1dJkSlknpyMq5u86qj1qW5mqWqLmvOzndNT3eKWo9vqDXj5TLfXbvvrF6YjILT+p"
                + "2iOuln+sWiLyy086HWFe+h7VHlZfH6lawsJ0wenglqdqr/2+blMt3G7l36GznZF/ac52HK9TU2gYLPyrURv/Mv3+Nubc38Cc+5vY"
                + "7ipzOkytXddg6eG6A8vqG3huHg1uNFTtptY3klWLafYv5U5Hpa9/2aTaPey/HFctlfx+uuh0eB78KV21V77300eqxVO6pDkdPl0v"
                + "DVLt3ksuLVAt3oU/jnM6qrx/vkC1+x04/1y1+N0vGuh0mG+d/UK1m7ud/Vm1mKfZC52OgBOnn6n2gDR7C9USMKrwkNMR9LjAodqD"
                + "lhfWUS1BOwvinY6Q9Pzuqj0kOH+FaglpnN/d6Qh/mPe+ag8/m7dVtYQXs1fUy2GvqJfL/lBveZ7AvxXNJ/5NaL7Rv8l7m38lmjdJ"
                + "X8d+otvYT4DwEOZc5/Fkuj6W7bdawrLhVhD3b25Fw9K/5L4uwEO2cf+m5Ipug5+M1QV4SAD3b37trdvgJ4d1AR6Syv2bR6t0G/zk"
                + "O12Ah9Tl/o17nm6Li3raWReaJz3ZwP2bp8G6rbnvi/G60GTT833cv3mRqNuaTPm7ji7AQzpx/6ZM1W1Rc14pugAP6cf9m5fPdFv9"
                + "tuUndSHi6qtfuH9T/rFui8jX9+hCWP3yk9y/0UfqNu7f6AI8ROB+im7Tbewn/PtiXWCb8etiDXYBvrFOX8d+otvYT3glmfsb+Pr9"
                + "bcy5v4ntrmuw9HCVcf/GdQeW1Tca6gI8JIP7NzeSdRv8ZJMuwEPKuX/zy3HdBj9J1wV4yEXu3/z0kW6DnwzS0eu/pHH/5tIC3eZd"
                + "eL5AF/wO/DiO+zfnn+s2v/tnv9AFc7eigdy/OfuzbjNPO/1MF+Ahhdy/sbfQbQGjChy6AA85xP2bwjq6LWhnfnddCAkuiOf+Tf4K"
                + "3RbSOA/fPvxsfnfu3+Rt1W3hvFAJ+jfsG/Vy+O1X9G8q+jcV/ZuK/k1F/6aif1PRv6no3/zP9m94nu3kXF4ldcp8Xkl18hxJnho4"
                + "pQ2vnjolXMybuntKsnh7avjkpcBzHTJxtWaHw8zvsFl0TT3XKl6Sp0xtNQD8j1r1hKVpnCzJk76Ok0Vl8laga/KAxiMlefy3ja2i"
                + "MmFT4x6ia0L/epIkj10uOUUlc4bkFl2ZSuhUSf5wTKi/qIy0hjYRXSO9A3kl1b6BHqIyNCgwSnQNueEPzoAh5suiMtDXv5LoGuDw"
                + "6y/JfQ76fi4qfZN9r4iuvoFeaZLcZZznZFHpctlzn+jqcqBSb0lue8BjvKi01Tz2i662N03zJDlxO60XlcRj5BJdiYfosSQ3m8ox"
                + "m2ZG/KbZiH/Hb2Cnx6/Fb/74d/zmj/+K3zR9wJymF5nT9CrbE+pIsmleQmNY1ic0gMXVJlKSK/Vu876oeIxv0110eezvdEuSvdI6"
                + "4zl7Tu4cKbo89/WuL8l+/XvvEBXfz3vPEV2+V/oXS7L/1HeniYr58rtposu/0gcFkhyYPXiuqAR6DO4mugKjRqiSHDo146CohPpn"
                + "TBFdoU3GeEhyPWnM76IiOcdsE12SOytEkhuPHB8gKo2tWWdFV+MeE2tJcpw8KUhU4uSJ50QXjvEtWsVP/k1UWg2Y/KXoatVz8qeS"
                + "3CGTvaLDYfiAq8PmsXZRyUobdxRvs3MmfGBceuYR0ZW1MtMm5mV1GlcZxx0z8ZyzPNrPZ2b7S2B2ab+eOSntwT+eAk/IOpkCH8jy"
                + "jWuAt183roEkjwvHsStzVHRPURk9JTpakseMjcbTHv1DZImoZPwYuR6+cTjyguj6MCCc/SHWskaSh1Wz3BFdQyfWPiMqg36onSrJ"
                + "7/2z9kbRNehFjT6ikj43YJck92tcQxJd6YfMNlHpuaXaS0nuFWWeLbp6FlWpLiqd4v1aSXKnNX7FoqtTH58MUWnzgTd8o83nPvVF"
                + "V5vxXkXwkLaeeFOJfb3mwk/SvFJFpTl59oK3PPAsg5+UsoXnxfEqqWz37AWm4jUX9jyvVKDLs4yZXqkx45gTk86cmA/ZruyDpUjB"
                + "O/W8pRwGZ27qJVHxyXirqiR7z0uFZ/rUT/taVKpU7+gL32iVli+6/Ip71IQ/2HqsleRqL3skiS7z7LefiUqNPu98LskBu97Bk6wh"
                + "DWwqKrXPDHRLcu3UgRmiq/bGwVtEJdxjyMeSbFkzuER0We6MaCUqkSUZKK2R60fMEl2RF0beF5XonqNuSHJ09KimoivaOiYHHtJg"
                + "7FJ4S4Mx1+EnDTKjRSWl/bhmkpwyMnOa6ErpwZ7Qfv64Y5Lc/hL7QPv1k3N5fdTJc4igJPN5VdXJS6nD1HNTwmkY6wmOw6e04RVW"
                + "OxxmZodMMGt22ExW6MkA2D9qhR4VVKUnLNAT0lhJiFhVyAo9sZIGJRlJBFXpQdYJ/SUnaZkz6klEUBU3WaEn/qRBSVCmoSpNyAo9"
                + "8SANSsKrqvYNjCLrkBvmy6RBScAZMMS/ElkHOHw/J61vsl9/IqjKFbL2DfScTFqXy15pRFCVfWTtAiUhra1WqTcRVGU/WdveRM2h"
                + "JR4zzUNPfTuh/PM8QtKgJI/R557KOsF68lqc77GRXo/z/fGfcb4/ml5kTtMHzGl6lW0JjWFZn1AH/eB5CQ1gcbV5nzQoSSQ6kr3b"
                + "dCerx/7OeJ6ekzvdIoKqRJIVerKDNChJfSKoyhyy+l55dxpp5sv9i4mgKmjr+1caPJe0QI8PCoigKt3IGhiVcZC0UP8RKhFUZQpB"
                + "i8f8ThqUxAO9WGnMNrJK7vEBpEFJQogaj8w6S9bGPSYFkQYlqUXEekLWOHnyb6S1GjAJ3wKq8iVZW/VkT+hwePKnRFCVZLKynpCW"
                + "lZYJHxiXPu4o3mbnTDzbLI9MGw1jPSEr9OQIcGX7S7japf185rdfz5YUvH0oSXvcezwFPsB6QsRKAk+AqsAHRkVHE0FJepIGVcFz"
                + "hJ6shz8cjiwhDapygawfBljWEA2rFs6+EWu5Q1boSSoRlOQMaVCVjWQd9CJgFzrDjWv0IS19bg2JrOmHqr0kgpLYSOu5xTybrD2L"
                + "/FoRdVpTpTppUJVisnbq4w1/gJJkkNbmA5/6ZG0z3hNvB0pSBG9p6zUXfpLm2Qse8sArlTSoShn8pJSPvWPY7tnLO4ZXTGU7KwkN"
                + "M9DqWYZ7Na+5MenMiRnHnJgP2a7gPUJJ9sFSpBzGXXPfqkoEJblEmk9GKjzTp35HX/hDq7SvSatSPS2frNCTtURQkprwDVuPJLKa"
                + "Z7/zOVHArrefkQZVwZOEnriJoCRNSYOqZJC19sYhHxNBSbaQFu4xuISsljsZKKFQklakRZaMmEXWyAujbhBFR4+8TxpUpSlZo61j"
                + "l8JDGozJgbdAT+AnDcY1I4KSRJOW0j5zGllTeow7RtT+EnsFVAU+gPddMc/2/3+erYcxTuOBxP0PHqfxQiK0ErlfMhSJNYb7JcOQ"
                + "KubZVsyz/X+dZ/v/Pb61yJhnu8gYU8gx5tnmGGMNFfNsK+bZ/m+eZ3vYmGd7GOkoNp5nexTpiCmU54MB6xjzbOtAkcOQKubZVsyz"
                + "/X+dZ8vzD+8g8Txbnn94F6kEtTzPs22D1B4bz7Ntj9S2Yp5txTzb/9XzbHmG6/fA740ZBN/j7kpIJtTwPJ88BonHv3k+eSxSExrE"
                + "6xcAa6HdPZjXLwDWRMt0IXw/DZjG63SgfdsO2A4tAh5NHQgcCF3nWKUNaEP+eNx9KHCoMcvWB4pMSDzblsdbvZH8jHHrU8BTuOJn"
                + "jML6IVUzxrALgAW4Us0Yma2GJBjj2XagHVcEY7xWQKphjG0XAYtwpYYxhlsDKcgY5/4R+COuBBkju0FIwcaY9yXgJVwJNkZ7g5FC"
                + "jfHvK8AruBJqjAGHIoUbY+FXgVdxJdwYFw5HEo1x8WvAa7giGqPFIlKkMUauAlVciTRGkCORZGO8/BbwFq7IxriyjNTIGDu/A7yD"
                + "K42MseZGSE2NcfR7wHu40tQYgW6KFGeMqT8CPsI7jDNGpeOQFGN8XQNquKIYY9UKUpIx1v4XkP/XlyRj/DoJqZUx7v4M+AxXWhmj"
                + "2q2Q3jLG4EuBpbjyljHS/RZSO2M8/iXwJa60M8a/2yF1MMbmdaCOKx2MMfEOSJ14nN7kQWlIOs54pLwTUlceszd54ponrnQ1Rs+7"
                + "IvXg8XuTD6754GoPY0y9B1JvHss3VcG1Krja2xhn7430No/rm8y4ZsbVt3k2KPCgEZXsDw98ByXLzLFI01j481hTIPpLA+DLQ9By"
                + "COVYCaMxO/RD7IHG7NDR2AON2aGZ2AON2aHjsQcas0MnYQ80ZodOxR5ozA79CHugMTt0JvZAY3boP7EHGrNDP8UeaMwOnYc90Jgd"
                + "uhB7IGqZNhwrQdlaZswOXYk90Jgd+hn2QGN26Frsgcbs0PXYA43ZoRuwBxqzQzdjDzRmh27FHmjMDv0GeyDPDuVYCSPPDuVYCSPP"
                + "DuVYCSPPDuW5y4zG7NAAqE0Q9IbnLR9BuyoE5yHQmRNoM1lwzvOW89AeisA5z1suRFunHs553vIZtGMa4JznLZ9DGyUK5zxv+QLa"
                + "H9E453nLl9G2iME5z1v+Ce2GFjjnecsOtAla4pznLV9HfZ+Ac563fAN1eTLOed7ybdTTrXHO85ZLUAe3xTn/9v8B6teOOOd5y49R"
                + "d3bBOc9bdqNe7I5znrf8BHVeL5zzvOXnqM/64pznLZehrkrHOc9bfoV66F3guzgj1DGDgINwVgn1xwfAD3DmhbphKHAoznyh+yOA"
                + "I3BWFZo+EjgSZ/54fmOAY0wj/yPOsyCDV0mdP5VXUl3wkdMxNXD+97x66vzbasrU3fPz1fCp4QuygOcOL8PVmofTmH94oGqZeu7k"
                + "GadjytSTW8D/6ORuWJqedTgdk74+61Dtk7cCLZMHFK92OsZ/W2xT7RM2Fe9RLRP6X1edjrHLnfVUe+YMp79qyVTuzHc6Phxzx63a"
                + "R1rvXFYtI70f8kqqfR+Wq/ahQQ9/Vi1DbmjgDBjibqLaB/pqr1TLAMfTrU5Hn4PPhqr2vsnPGquWvoGlh52OLuPKFqr2LpfLuqqW"
                + "Lgde7nQ62h54tUS1t9VedVEtbW+WT3M6ErfrQ1R74jHdoloSD+kBTgfHeVR7M+N3bDwua/yODXZ6/Nrv2P749+/Yqv/X79gu1mbO"
                + "xRjmXGzI9sJ7TodpXuEVWNYX/gKL6/h1p6NS7+MbVLvH+ON7VYvH/gN1nQ6vtP14zp6T919XLZ77dlxzOvz67+ij2n0/3/GRavG9"
                + "8lW00+E/dcs81W6+vOWwavGv9EWS0xGYvX66ag/0WP+dagmMypWcjtCpn3VS7aH+ny1QLaFNlpc7HfWk5TVVu+Rcnq5aJPeSu05H"
                + "45GLH6v2xtYlLVVL4x7ZvzodcfLCh6o9Ts6OVS04xrdoFb8gULW3GrDgA9XSqueCGU5Hh0z2ig6H4QOWDpuXKao9K21pe7zNzjnw"
                + "gXHpOR1US9bKHKuaktVp6Uscd8zBc87ysE1lpq0pmF1sQ5hz6ij4x0/BE7JOnoIPZPme/QVvv+7ZX5yOceE4tmSOurxbtY+ecrnY"
                + "6Rgz9jKe9ugfroWq9owfrw2Bbxy+1ky1fBhwi/0h9vYIp2NYtdthqmXoxPvxqn3QD/dPOB3v/fP+e6pl0Ivfd6j29LmPejkd/Rr/"
                + "rqqW9ENuq2rvueXPyk5Hryj3x6qlZ9GTP1R7p/inp5yOTmueRquWTn2e56r2Nh+8gG+0+fz5NdXSZnxpHDykbRneVGLf0unwk7TS"
                + "E6q9OZXtgrc8KPOCn5Syhf+fK14lle2evcC0e82FPcUrFWjxLGOmV+qFHOZc2MacC5+x/XRXWIpO45163jqdBs7cH5qqdp+M7584"
                + "Hd7zfoBn+tQ/1E+1V6l+8Dl8o9WhZNXiV7z7d/iDbfdwp6Pay90FqsU8+xs/1V6jz7ahTkfArm14kjWkTZdUe+0zm/ydjtqpm3JV"
                + "S+2N6weo9nCPz2c7HZY160NVi+VO7inVHlnyGUpr5Prcmaol8sKqYNUe3XOl6HRER6+8pFqircvHwUMaLMuCtzRYHgk/aZBTrNpT"
                + "2i+94HSkjMyZp1pSerAntJ+/tJ3T0f4S+0D79QsyeH3UBR9xnGf+VF5VdUGWfmTqufm39XWsJzgOn/89r7B6OI2Zh5dxnOfwQN0G"
                + "PdkC+0cnz3Cc5+RuWKAnusBKwnEeHNugJzZdgJKs5jhP8R7dNqG/s54uZM64rnKcx+mv26Anbl2AksznOM+dy7oNelKuC1ASXlW1"
                + "78OfdduQG+4mugAlmc9xHu2VbhvgeDZUF/omP93KcZ5njXVb38CyhbrQ5XLpYY7zlHXVbV2gJLrQVnu5k+M8r7rotrY39SG6kHis"
                + "fBrHeXSLbuM4jy5ASQI4zsO/YmQ9ee33jgEcw/lvv3es/p+/d/zjYgxzLtZmzsWGbC+8Asv6wnsc5yn8BRbX8Q26ACW5znGe43t1"
                + "m8f+/XienpMP1OU4z/7rug160kcXoCTXOM6z4yPd5ntlyzxdMF/+KprjPFsO6zb/Suun60KgxxdJHOdZ/51uC4z6rJMuhPrnShzn"
                + "+WyBboOe1NQFKEk5x3mWp+s2yb0YiggluctxniUtdVvjHgsf6gKU5FeO82TH6rY4eUGgLrQasLA2x3kWfKDbWvVkT+hweMEMjvPA"
                + "B2ysJ7qQlZYDHxiXvrQ9x3ly8GyzPHLwVFhPdBv0pANwpa0px3lsU5lvG8KWU6s5znPqKO49fgo+wHrCcZ6zv8AToCrwgVGXiznO"
                + "c3m3LkBV8GyhJ0M4znMtVBegKs1024cBt0dwnOcW+0bs7TDdBj05wXGe+/G6AFV5T7cNevGoF8d5ft+hC+lzf1d1W/qhPytznMdt"
                + "1YWeW9wf67aeRU9PcZznyR+6AFWJ1m2d+ryYxnGe57m60OaD59d0W5vxZXU5zlMaB29pWzodfpJWtovjPKUndAGq4gU/KeVj7xi2"
                + "e/Z6cZFXTGU7K4m+zkCbZxnuFbzmXtjGnAs5zLnwGdtPJ3Gc53RXWIpOp+Guud8/4TjPD011wSfjB3imT/2DzznOc6ifLlSpfihZ"
                + "t0FPhnOcZ/fv8A3b7gLdZp69bSjHeb7x0wWoCp4k9MSf4zybLukCVCVXt9Xe+PlsjvOsH6AL4R7rQ3Wb5c5nDo7z5J7ShciS3Jm6"
                + "LfLCSpHjPKuCdQGqckm3RVuXZXGcZ/k4eAv0BH7SYOkFjvPkFOtCSvucebotpcfSdhznYa+AqsAHKuI8FXGeijhPRZynIs5TEeep"
                + "iPNUxHkq4jwVcZ6KOE9FnKcizlMR56mI81TEeSriPP+3OM9/rLeYedgsZKW1n8+Y0t4sjDse18AsZNaN7mkWRk+JLDELGT+Ge5iF",
        ""
                + "obG1z5iFQT/U6GMW0ueabWah55Yq1c1Cp3ifDLPQ5gOvIrOQ2NYr1Sw0J0aew8Co7AMWpV4yCz4ZaV+bhSrVe9Q0C2bb28/MQo0+"
                + "A5uahdpnBm8xC+EeI1qZhciSkffNQnTPMTlmgecPmIWU9pzD9vMzDxNxPhlT2hNxPok4n0ScTyLOJxHnk4jzScT5JOJ8EnE+iTif"
                + "RJxPIs4nr7QYM45R2QdEPlFlIJ9EnE8izicR55OI80nE+STifBJxPnnsKjOaiPNJhPyZjJUDQ4x18IKNcYhaxhp6gcYKezWMkY/q"
                + "xup8/sbafdWMsZYqxrp/vsaqgN7GmoGexmhWJWO9QRM+L9ioP/hzahif4G/cW8W4y9vgVzKYwQanunH1X3OxTYalGo7ijRUMw4z1"
                + "CsON1QkjjLUIJWPlwUhjncEGxqqCDY01BBsZKwZGG+sDNjVWA2xmjJm1MFb6izPW9Ys35kiHG6MVkjEm0cCY1cyjB02NMQHmxxnM"
                + "cIPTwLja9N/9frY0wlGIscZiiLEiJBlrLJKxPqTJWGPRZIy4eBhrLHoYKz1WMtZYrGSs/FjZWGOxsjHK4mmssehprAvpZayx6GWs"
                + "EultrLHobYys+BhrLPoYa0j6Gmss+hprSvoZayz6GaMpVYw1FqsYK05WNdZYrGqsP1nNWGOxmjGCYjbWWDQbq1P6G2ss+hurVQrG"
                + "GouCMWpS3VhjsboxHhJgrLEYYKxsWcNYY7GGsdJlTWONxZrGyGSgscZioDFOGWSssRhkjFzWMtZYrGWsklnbWGOxtrFmZrCxxmKw"
                + "sYZmHWPNyjo8mvIf5Tsnza1lpdmmMp466tbGHT/7i1vLrHt5t1sbPeVaqFvL+PFWuVsbGns/3q0N+uH3HW4tfa7b6tZ6bnnyh1vr"
                + "FP881621+aA0zq0lti094daaEyPHrhhPdwUW/dDUrflkHOrn1qpU3/27WzPbvvFzazX6bLrk1mqfWT/ArYV75J5ya5Elq4LdWnTP"
                + "5ePcGseN3FpKe85h+/k5aVyybVMZTx3l8s1j95xPLt/XQrl83yrn8n0/nsv37zu4fLutXL6f/MHl+3kul+/SOC7fvFIy55NL9oUc"
                + "xtNduXz/0JTL96F+XL53/87l+xs/Lt+bLnH5Xj+Ay3fuKS7fq4K5fC8fx+U7p5jLN+ewonxXlO//HeX7P9YbKsgQlVPjCtMlmVFU"
                + "Tn5SsEt0nRpXcF7MO3kXFtfJQliUk4XOi8xxOpjj/Jntt7vDcvR2Jiznbw/HXTXu7pPkvHV33fjMfXdvwvL8V02SC6o87CIq+dEP"
                + "m4mu/LGPnkjyaa/Ho0WlsOXjjqKrcIJ7hySfmf1na1Gx5/9ZU3TZXz71k+Rzk5/uF5Wic09nia6i8uevJPnClRfHROXCWy8+FV0X"
                + "RpdNl+TiRX93EpXimn9XE13F8S8nSfLVnFe9ReVq7KsA0XW1+6u7knz9fPl5Ubm+tHyB6Lq+sbxAkm810/eJyq3q+hjRdasO/5+O"
                + "JXt4Dm7JNt0mukq28zFHVfn/0tQFnmvLdhyvE/P0dbC4OKoKju1BLnMe5DDnwXK2lwRLcnnBnVJc3VfiAcuYmwck+dXdm1NEpfz8"
                + "TeShfMH1qZL8ctL1UFF51ft6vOh6FXB1oiSXTf/5paj83elquOj6u1rxMUl+/qq4oai8OFbcR3S9+PTiIEl+6ncBz+Tp/gv3RNfT"
                + "WedHSbJ7x7kdovJn63MlouvPmmcfS/KjJ2ctovJ49Nl3Rdfjjva/JPlXzR4oKg+72N8WXQ+bnZ4tyXf3Fa4TlbvuQryjuzcLN0vy"
                + "7e4FB0TldmbB36Lr9nD2CqeDvcJ5kf3B+XNBBmnsIaSd/ARI8JBdOC4sOE/D2E/Iyn5CVvYQ5jgvMsf5M9tvZ8Jy/nZ3WI7eHg5O"
                + "jbtufNq+u2gzwVtuwvL8YRfS8qN/1YjgLc3Imj/28WjSCls+ekIEb+lI1sIJf7YmzZ7vhnLDW2qSFX6ynzR4CDQY3jKLrEXlL46R"
                + "duGt56+I4C2fkvXC6L87kVZcs2w6EbylGlmL41/1Ju1q7MtJRPCWALJe7V5+nrTrS1/dJYK3LCDr9Y36PtJuVS8vIIK3jCHrrTo8"
                + "qxIeIhCV7OEIOvvJv6Pv2r/WGzJWGOI1hzj6vs6IxNuMGDw8hDkPcpnzYDnb75SCua8EtUR5QYkHLGNuTiENHnKACN6CPMBPQkmD"
                + "h6BOg7fEk/VVwM8vSYOHTCQqm341nKzwk4akwUOOEcFb+pD1xacX8Eye7r84iAjeco+sT2ed20Han63PjyKCt5SQFX5iIQ0e8pgI"
                + "3vIuWeEngaTBQ/4igre8TdaHzQrXkXbXfXo2EbwF7+juzYIDpN3OLNxMBG/5m6y3h7MnwEPgG04Hv32874r1hirWG6pYb6hivaGK"
                + "9YYq1huqWG+oYr2h/+H1VBNzVfupcQnbnA5G1X7yk8RequXUuMQWasrJu7BYThbCYj9ZKMUwR5KZI0Wx3bIXlqOWZbCct6zFXTXq"
                + "dHU68tbV8cdn7qsTAcvzIAEtnyqB+1V7fnTgBdWSPzagqtNx2qv6StVe2LL6IdVSOMHcx+k4M7vaSdVuz6/2u2qxv/R75nScm+zX"
                + "RbUXnfObqVqKyn0qOR0Xrni3U+0X3vKeoVoujPac63QUL6p8ULUX16z8l2opjq+U7XRczfHYqdqvxno8Vi1Xu3uEOB3Xz5taqPbr"
                + "S01TVMv1jaYkp+NWM+qq2m9VpxWqBe1WtM24f6PaS7aRVbVwu1W1c6vV6WDk35izHcfD1BS0XTUco9UKjq1WBnNqjWNOrbFsD7nv"
                + "dJQXhHrj6r6QcljG1O3sdLy6W3eBai8/Xxd5KF9Qb77T8XJSvRLV/qp3vTOq5VWAvMjpKJvesLJq/7uTfFu1/F2tcTun4/mrxldV"
                + "+4tjjXeolhefNt3kdDz1i8Ezebo/po5qeTqr+Sqnw72jRR/V/mfrFqGq5c+acQFOx6MncS7V/nh03Feq5XHH+GpOx69a/G+q/WGX"
                + "+O2q5WEz5WOn4+6+hGGq/a47Ae/o7s2EgU7H7e6JnVX77cxET9Vyezh7hdPBXuG8yP7g/Jn/x3X2EF04+UnCNu67JPbCcWFiC30d"
                + "+4luYz/RbewhzJFimCNFsd2yDJbzlr3cv7GsBadGHX982r46Xbl/UycClueB+3UhPzpI4P5N4AXdlj+2+kpdKGwZUJX7N9UP6bbC"
                + "CdVO6oI939yH+zfVftdt8JMuugAPecb9G7+Zuq2o3LudLlx4y6cS92+8Z+i2C6MrH9SF4pqec7l/U/kv3VYc77FTF67GVsrm/o3H"
                + "Y912tbuphS5cX+oRwv0b0xTddn0jddWFW9VNSdy/oRW6jfs3ugAP0bh/wz0U9pN/9W7+tZaq0cexGn2cYcYvjLV/925s7CHMqZXB"
                + "nFpj2R7qDea+kPvcvwkph2VM3QW6AA/pzP2busgD/KREF+Ah87l/U++MbnsV0LCyLsBDFnH/Rr6t2+AnV3UBHtKO+zeNd+i2F5/G"
                + "4Jk83d90E/dvYurotqezWvTRhT9bN1/F/ZsWoboNfoLeOjwkgPs3cV/pNvjJb7oAD6nG/Zv47brtYbOEYbpw1618zP2bBLyjuzcT"
                + "O+vC7cyEgdy/SfTUbbeHsyfAQ3J57WF++xX9m4r+TUX/pqJ/U9G/qejfVPRvKvo3//PrqWafEJW5DxZeFJV5juxiSZ73W3YQjptk"
                + "J4h583pmh4q3593OPgOMPVKKq78f2cP8IwWia15snockz59zSgV/1qkHsPxYdEeSF3YtuiMqCxhdC3KvTJTkxQlXckVlUZsrS0TX"
                + "omznMkle1orjQDl9nHmiKye3pKYkr04rmSkqq8aVvCe6Vu1+eFOSP2/zW4aorPvkt1TRte6IhrxtFtxTRWXTSHea6Nq07unPkrx9"
                + "9bOVorJdeDZQdG2PLwXnu+/KtorKdz3KMkTXd6NfHpHko6mv1onKUc9X/UTX0VrlMyQ5P0wfLir5VfQI0ZUv6AGSfL4ux2zOKxyn"
                + "Od/s3/Eb2PWA1+I31f8dv6n+X/GbSz7MuVSXOZeC2W73lOTyGaf/wtXhp1/AEvGDLMkvj3z/SFRerfteF12v+h1cK8mlxQcVUSnb"
                + "erCP6CrL2DVTkp/+vPOFqDxbuStcdD0buPWFJGvFW3uJinvq1tmiy522IUqSH978Es/kt4wvN4qu31LXxkhySc016aJSMnPNZ6Kr"
                + "5L1V9SXZuWxlMkdfVn4iupx5yyIk+crEnChRuZKbM0F0XVmyJE6Si+4sjhMVIDhFdxaNkeQ8j+yRonJKzT4ouk49yL4iyUdK2SuO"
                + "7IEPuI4UrMgRlSWHl63H2zywfIOoLN22/EvRtWT08i/EvCUHl+NbLDm0/B445UdvMfPoZ2DuP1rEnLwn4KfmXZDkJcl5v8LypKgE"
                + "b/9KUYkkLwXCBxYULxGVFWOLv5Pk5VOKc0XXishr+OufeVyvBd+ofe2u6Fr9/e1s+MM/bz+R5LXf3N4Or2hzf6OobLj6AE9mQ9D9"
                + "M6Jrw5e/PxeVrY8efS7JWzs8aiC6th53TxaVndv+jJXknaJ7l+ja+c+nfqKy/+FTVZL3z38KX9r/4/MtonJs5Yu5knys3/N7ouvY"
                + "7FK8nfxPyyR4y/jSj+En/yhFSTmXW7ZLks/tKfMSXee+ZMuLiy+Qz9ITbC/bVTodx9Nhzys9AXSVeTGz9MTFT5hzMZc5F+ex3T4a"
                + "FsXeHxbJPgKcj0/MEJXnW068Jckv5p4YJbqe3zt8WVSe+h1Gj/Gpehjv5WnaXrwF9+S9fpL8Z+zeD+EVu7b/JCq/P9/eVJIffb79"
                + "K9H1qMGWH0Tl/sbN5ZL8IGoL/tb9M19+KCq3s79Aubv95Mso0XV7+9qHonLt4lqS5Ou11m4VXdfufva2qBQvWf2eJBd/9xlHQ3NX"
                + "HoOHlKw4Bm8pWZkFPylZIYtK3pPl9eAtF5Y7RVfer+wJR28tw9M++hn7wNGi7BOkzX2QXUw077eFF0mDqpyhDvNisxNoGOsJjm9n"
                + "B8He5MgeZh4pBfP3IwVknRd7SoV9Vp4HEVTlASzQE9JYSYhYVcgKPcklDUoykQiqsoSs0BMHaVCSZURQlTyyQk9mkgYlqUkEVXmP"
                + "rKt2/5ZB2rpPHt4kgqqkknXdEfdU0jaN1JBPqAravpvWPVtJ2nbhKXpoUJWBZN0eX7aVtO96lIIDVckg63ejX6HXetTz5REiqEo/"
                + "sh6tpQ8nLb9K+QwiqEoEWfONvi6UBH3H83W5f8t68lqcL4DTf4vzVf/POF/1S3WZc8mHOZeC2X76LzCH2z3RD55x+gUsEd8/Iu3V"
                + "uh9k9HqPfK+TFXqikAYlWUsEVelD1rKMnS9Ig5LMRB/3513hZIWe9CINSvICWSneOpus7rQv8Ux+y9gQRQRV2UjW31LXpJNWMnNt"
                + "DHrwNdd8RtaS91Ym8yjPqvroxS5b+QlZnXk5UaRdyV0WQQRVmUDWK0sWx5FWdGdJHBHrCVmL7mSPJO2UumgMEVTlIFlPPWBPgJJc"
                + "ITpSCh+wsp6QtuTw8g2kLd22bD3e5oHl92ApX/4FDWM9Iev/ae/ro6Iq175v8IBgOu7yG3FnM/g4JqGlxZSQ4uKMpp2W9sFrYn6F"
                + "RQqmhqit0jQL8ANMTXgO5id5fDl+9Jh1cEsJDOhWCFE0d7RvlQFCEGsPh6+h9H5/137e87721/Os9fzTH7NY97WGa+7Zc82+r+u3"
                + "N/O7fjfAk72wSaez8ezJ0zdp/uly8rguMgYkacNrJ7ma4AGeMEZIgkwg6wSenGAMSJLJDKBKDnPuGPHjIOTD4BpEku1f08CcwJM2"
                + "xoAkm5Eb62vzkRWTb+FsAEn2M2PftcbzzLlv753djB2a0tLJDKDKSOY89E3reMaAJKnMOHrYc4w5gSecMSBJL2YAVZBLJyu7PmYM"
                + "SJLHjMKdnT8xZ+GH3TZkyAovVqp0o/d95Mk60tNWfOFFvQBVApmT8IQZXVXk7z7WhTiBKvATkrBFpnV2B3rfhf/dqhyaU/UBzalK"
                + "I786Gx6bmgSPQ30Tr3q/KIYxIMl7zACqLGFO4Mky5AMvuMwMoArWBXjSizEgCdYCqPIWsuJY/hjGgCRXmQFU+Zw574w8eI8xIMkZ"
                + "ZjTuz8N7NZ7fg1oDkrzFjNrNex9lTuAJUhxI0swMoMoh5qxp2DWPMSBJHNItM5tY85wdhciQ+p2FyBbgCfKkfnsYMuTiDjszgCo6"
                + "c7qaPsHZBpIgK07fpBzAevt0tj6drU9n69PZ+nS2Pp2tT2fr09n6dLY+na1PZ+vT2fp0tj6drU9n69PZ+nS2/639VFMmcfXjWyvH"
                + "cjVNS3lM19JupzTjcUTKOR6dNjOlnj+cVpsSCTve2RPPtjhn0HznBC6njY+6p2vpH0XbMH9D9GB4KscP07VNfxk/jKsZZOWMnNFb"
                + "dG3r06MTubpl8ugVXN6y2fa2rn3yLPXDbXvZFs3lbTlDW3Rt19ShG7j66bKh+7n86fEBj+ja7sn9c7ia+0H/Ii7nnuqL2A5KlnSu"
                + "HlhsKeDygdxej+pa/q7gJK7mS8EHuZwfGYg5J04EzObqiRkBOVw+kdRjiq6dnuS/iKunA/z/xuXTg/w26lrpMPZXrpY+wG5wuVRi"
                + "P+sa8Txc/c5B/Wr0vazZxwa/eOi+PrYH/28f2y//v48toovmRNykORGN5I/8Vdfuvefog2ffcATB88gkTdd+OxXTj6t3c2P8uHz3"
                + "1efe0DVv9XMqV7sPPXeEy92JMzfoWvv3LwZxtWPnzFoud7z2apCuGdWvHuOqZ/Wr73PZM3Xe97rWfGM+zsntxPnzuHx70qIqXavv"
                + "/8ZhrtavfeMtLtfPW1yja/onS0qpC23JB1zWXUtv6NqVlLe/5+qVnLe3cvlK5vJyXSuvW1HOVVjMKa97Z4euufxTdnG1hKdM43LJ"
                + "rZRwXTvlpaw49QVyQD5VlrSMq5kFS1/Han6VPJ+rWYeTF3A5Myl5IY/O/DoZnyLzH8khmHMvdjjNjH0LM0/GPklzonpj/qSox3Ut"
                + "MypqEDxt40Ox+lfGh+paFixyICN8BVd3LA1/Qde2rwpP5PKOESPw7tn+YU3IjcEjhnJ517fySuTDerm3rv31f8txyIrJg+dxdd+1"
                + "QTgz+wYOjuTyvr39grl66M5DCbp2aMpDP3D50DeWTVw9erhPha4dtVpe5PLR9b06uHqyuZdN106m90IunawMmsPVwp0939W1wleD"
                + "Qrhc+GEgVqd0YwBHtqwI/BB5si4QlVKRE/CirlV8EdDN5Yq95Omq6ok4vUXk7z4W+DEevwt/tLcIVu4OpJneojEf0JwxiTRnzBry"
                + "R+6ExxF5CB5b5L9jzvsTN3K1M2/iGV3r+njip1zu/GlKBFfbe03ZhtzgU7Au7VNfwCp4Ul/o0LXW8S9kIyuOvTKaqy2dr1zStTu7"
                + "X4nn8p2R8TFcbdw/x1/Xbj0aj/dqPD8/m6u1mxeg7mrb5n/P5dr8RQO5WlP1BtO1Hwctms3lmobEfK5WZ761X9eqTyRSV2jOklhk"
                + "SH1SLLKlfkkW8qQ+SeOqqy1ZR7ZcTA7jsquJMuH0zaU426ezKQdOl6dMEtLHt1IeI55n5VghAVUixam08SnnRC7hCR7XpjTDH+Gc"
                + "QTOdPYnncU4QStr4aBv8G6LuEc8TPRge4ImQCEmI58FjBXiSKCQgyRbieUavEArwxC4kIMnbxPPYooUCPNkgJCBJC/E8Q/cL5dPj"
                + "/XOElPvBgEeI5+lfJJTcU5Z0IR1Y3Pcx4nksBUI5kBucJKR8qdejxPMEHxRKfmTAbCGdmBH4GPE8ATlCOZHkv0hIpwN6TCGex/9v"
                + "Qjk9iP1VSKUP+G0knofdEEqpqdcCkvxMPA8xNYQn9/U7/mxyPff3O/7y//odH4y4SXMiumhORCP5HX0w843IX4nncQTB80hMPyHd"
                + "zZ2kEc8T4ycU4AnQCkjyBvE8zx0RSnfii0FCApJsIJ5nZq1QgCfHhAQkCSKe59X3heKZOh/n5HbivO+J55k/Tyi3J71xWEj1axdV"
                + "Ec/zxltCqZ+3pJQ6FBfXEM+z5AOh6K63vxfSlZylN4jneXurUK5krigXUnnd8nLieVZgTnldyi4hlfB3dhDPkzJNKCW3KBOAJOHE"
                + "8yAHFMITIWUWJM8XUtbhpa8Tz5McAs+95IUil/BEKMCTBbBJsW8RzxM7nObHPkmeqMeJ54nqjddOihoED/CEeJ7xocgEsgrw5AXi"
                + "ecJXCAmokiiUHSPCmojnGYFIsv1HDBUK8KQ38TzySuTGejkOWTF50PfE8wyeJ6R91wZHCmXf3ocSiOfpFywkoMoPQjn0TZ8K4nks"
                + "m4R09LDlRaEAT2zE8/TqEBJQBbl0srLnu8TzBM0RUuHOoBChFH4YwInnCcRKlW4M/BB5so7+P17FF4GoF6BKt1AIT4TUVUX+7mM9"
                + "ESdQBX5CEpFrWqU7MPBj+N8dk0hzxnxAc8asIX/kIeJ5InfC44j8d7zq/YlniOeZuFFIQJVPhQI82UY8z5QIIQFVsC7Akw7ieV7A"
                + "WgBVspEVx165RDzPK6OFBFSJF8qdkXP8ieeJjxFS4/54vFfj+QVvE88zP1tItZvnfy+U2vw3TJ5n0UAh1VQtmi2Umoa39hPPk5gv"
                + "pOrMROoezkmKJZ5nSSyyBXiCPKlP1onnSdKEBFQJE4qraWkC8TyUFadvUg74eB4fz+PjeXw8j4/n8fE8Pp7Hx/P4eB4fz+PjeXw8"
                + "j4/n8fE8Pp7Hx/P4eJ7/7n6q2/dZpMyC0zfJutosUtak8nqLtO1KdaZF2rG0psoiZfvXbrZIuesb91ukfddaOi3SoTueVIt09HB7"
                + "L4t0srkzzyIV7vQ6LFLpRm+RRarIIUsaBrJqEqyj6D2L1JlXcNkitff6j20WyZOaf9UitXTmnbFIjfv3vmWRajf/tdki1VRlx1mk"
                + "6sydhRaJ9AMWydVGEZ6+uX0fYxQnWVcbYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnYxQnWTUJFnEyRnGS1vs/"
                + "tjFGcTJGcTJGcTJGcTJGcTJGcdJ3VzvsjFGcjCE+336Lvv0W/0D7qSbP9xiZBbHDyUb19hhZk8aHeoxtV8JXeIwdS0eM9RjZ/vJK"
                + "j5G7fvA8j7HvWr9gj3HojmWTxzh6uFeHxzjZHDTHYxTuDFQ9RunGwEkeoyKHLHFXZCN3wjombvQYnXlTIjxGe68XlnkMT+oroz1G"
                + "S2d8jMdo3D8/22PUbl400GPUVCXme4zqzCWxHoN4I4/haqMIT99Mnk+VHTucbFRvqm/67p7ipPoeMZbqW15J9T14HtV3v2Cqb8sm"
                + "qm/ar4LipPoOVKm+aadkipMqe4xZ35E7qb4nbqT6nhJB9f3CMqrvV0ZTfcfHUH3Pz6b6XjSQ6jsxn+p7ifnddJJG9U0R+urbV99/"
                + "nP1UqU8jNYf0takLbfZ0y6p0UuWmrrPWpkWvGmx1pc1aNQ6PPasmk0J3SgHNnNIPM6Up6VZ32sRn58D/UfR38Jx49gl4+JN2qyNj"
                + "9vjjNvumrU8GWN0ZJ8OdVseWuaPdNvvWnPBRVvcWxaZbHds22rbY7J+U2cqs7m3u0L5Wx6fK0Ks2e3aPofes7l0hA/ytjtzm/txm"
                + "/+yZ/p1W9+7nLZetjgOdllKbPe8vlkqr+2B88G6rI780+HOb/ciw4D1W999HBaRaHV9GBOyy2U+mBayzur/c4b/C6iiU/PNt9m8S"
                + "/DdY3YUp7DOroyyW3bbZz2awk1Z32UFS31buY3Nt9ovnWajVXdn5n3pcZpDKliXY7GI3c5L6lsVaXaKQ7bTWCTeTYZPGVNGciL9h"
                + "zoExa6xuUf90OGa+HtkIzwDHd/A8P3m+1XE3M6a3zX43LuY3q/vu2uk4n92bnmuy2bsXT+NWd/f6l45YHR0JL75ks3fEvzTF6u5Y"
                + "GL/G6vBEzLbZ7J6o+AFWt+eJhR9bHc33Fjxls9+2LQyzum8HJ35tddR53ky22etHJ8ZZ3fX+yS1Whx6W9HebXU9JzrS69QkrHrI6"
                + "qpVlms1+RV7+tdVdfW3lQKvjgvZOnc1ePjOl0Oq+8GvqbaujOG/ltzZ7ybjUHVZ3cSVlQsHU1Pk2e8GdVY9b3QWroxjtoxtl0O67"
                + "zw602YtmR+XTLrtRnVZX8Zpop7W2+L2oNthHR36FZ2eNlGj+yEk42ihbHDy5VhUe1eqBZ9nDD9jsxUvk7TjaHvkrq7skNuQnvHtZ"
                + "yHqrwyVC9lrdroMDu2z20ryBh7FeDQPPYKXS+s3Eqol+iF+d2s9udZ9rlPxs9vMVfZFR5aF9G/FZlD6I7Tu/PkFY07g+va3u71p7"
                + "7bbZq7J6fW51XLrV66DVfenDoL04G4lBZ6yOq8VBOCdX5/T8N5tdW9tzrtXxw+WeL1jdPywNfAbnsCQQ5+F6UOByq5sfDkAO144M"
                + "nGR1uGcE4vzXeulxV1WAjnUM6zmWFLeBVmud92jgS1aXaeu81wM/JoXusOcwRw4dTnNCV1ndXv2RYza79+zwRHhWD1fhyRrRarN3"
                + "1YTtwTEPjPC3urtOPBpks3cusB+3OjpjRvW3ujunPVZrs7cnhCOq9vjwO1Z3+2uPv22ztzaPDbE6WrvGrrW6W9vG59nsv4hxL1sd"
                + "Rtg4fDpjSORym73l+FOozZbmpzKs7hbt6QqbvSnIsdnqaJrlaLK6m2ImIGcaQp5ZbnU0rHsGeduwIDrAZr/ZPgF1V7s0Khyfd3o0"
                + "KlTPj8KKcEc0wznp++xgnLfplBWaEY281YpSc0hluyqdlLipCxkDqkwm9e2qwWwR4QmbkuZJXQcbPaUfnpWmFND8KenMmTYx+jt4"
                + "Tjw7B/M/evYJePj444wBSezMyJj9ZABzZpwc7WYMSOJkxpa54aOYE3iyhTEgic4MoEoZc25zD8U9d3aP0L7MAKrcY85dIf05Y589"
                + "M8CfGUCVTuYEnpQyBiS5zAygSiVzAk8+ZwxIspsZQJU9zAk82YX7grSAVGYAVdYxJ/AEV3UgyQpmAFU2MCfw5DZjQJLPmAFUOcmc"
                + "wJO5jAFJGDOAKqHMSXhi9mgkMAYkMUhxi+vTn0USi2WLCE/w2M2cpNCN+BvmHBhTRXPGrGFOUR/ZCM+Ap8Px7OuO7+B5PgZ3N3fj"
                + "Js9nBlDlN+a8u/a5Jsa6F0/Hee7eNI0zZ/f6F19iDEhyhBkdCS9NYc6OhbNtuFuJil/DDE9E/ADm9Dyx4CkGGFz4MTOa7y0MY87b"
                + "wW8mMwYk+ZoZdZ7EOOas90/6O2NAkhZm6GHJmcypT1iGe5kr8oqHmFGtLP+aOauvvVOHe5yZKwcy44KWUsicF35d+S1jQJLbzCjO"
                + "S93BnMWVqbj3AZIgKwqmrnqcOQlPaNflZ3GvBCQxaK/mqDY2pfjRqE62iPAEj9+Lyqcdm0dKNHPkV5g5a+QkHG2UVYVHteEvKKCK"
                + "B55l8nYcYc/DDzAGVPmKOYEn65kBJPkJkZSF7GVO4MlhrFHDwC7c3+UNPIOVSuuHmIEkM7GCop+dOc819kUWlYdKuEcDqjTisyh9"
                + "grCOcX0QJ1ClN3MCTz5nBpBkN2NAlYPMCTw5wwwgyV6cmcQgnBPgyVxmAEn+jTGgygvMCTzBZweSPIPzWRK4nDn5YdxdGu4ZAchn"
                + "oArOP+EJ6XF7jiWVbYCONQ0L/JjUt4EvsUWEJ+zPwBMr7NHQ4TRn2HOYI4euYk6vPjwRntWPHMNd6tnhKjxZYXtwtAMjWhnrqhnh"
                + "z5xdJ+zHmdEZ82gQ7mEXjOrPnJ3TwhFVe/xjtbifTQi/w5ztr40NYUZr1+NvMwZUWcucrW3jXmaGETYef/0CVfDpjCFPoR5bmiOX"
                + "4873+FMZzNmiOTYzo2nW0xWMAVWamLMp5pnlzGhYNwH5A1RB3jYsmIBaq10aHcDYzfaocHze6VFYESAJqlXPj6ZeyL6UCZrx7GCc"
                + "t+nRyFutyNen8V/0aTCzT4Nh0F8D9BdCDww/oA3tY5WAQX0atI/VInO3Kl+fhq9P43/WpxGBqhqNQX/5jUd1PYZBeyNRn8YmjK34"
                + "oT6NrRibfX0avj6NP3SfhmL2aSgY3+KH+jS+xSjwCyU+EXaQ2acxCEg9DMPXp+Hr0/if9mkQf12HQX0axF83YNTjak59GrEYU/FD"
                + "fRpTMSb7+jR8fRp/6D4Nf9z/noY9bfYJn8adcSAGMzsAWmBbzG+FqR/pcYwxxGT7hcCG4N57HvW/wQ4ELm9B9v4Z9s/U54l73Mmw"
                + "k3E/Qb0Zc2FpD1Y/c49IP4y5eMdX/R5i/sTrmp0a1KFRAOS+f+9I2p+yCLYICH3/PpK0c6UL1gUkvn9PSdrV8izsWSDu/ftL0n6X"
                + "52HPA1nv32uS9sKsgK0Agt6/7yTtknkR9iKQ8v49KGkHzcuwl4GI9+9HSXtrXoW9CuS7f29K2ndTg9WAcPfvU0k7cv4I+yOQ7P49"
                + "K2m3zuuw14FY9+9fSft41sLWApnCzZ1CwzHSsDqj4YnA+Ol3+1rS7p+3YG8Bae7f45J2Bv2ZkQbF87v9LmnPUA+sB8hx/96XtJ9o"
                + "G2wbEOL+fTBpp9FO2E4gwf17YtIupN2w3aj4+/fHpP1J78LeRWXfv1fmNDYFnmnEaf9u38y/wNeD/YX47d/toTkDvkA2g7ju3+2n"
                + "+RJ8wewl4r1/t7dmHHy9WRxx4NSbgQz8X9RTgLuLOHheJT6cuoiQv1+bfQVzWALuEObgut+fuoVw9U8w+wreZItxZX/T7CtYzJJx"
                + "1V5s9hUks2W4IiebfQXL2Du42i4z+wreYam4kr5j9hWksjW4SqaafQVr2Hu4Aq4x+wreY+twdXvP7CtYxzbgyrXO7CvYwD7CVWmD"
                + "2VfwEUvHFecjs68gnTqh8PtkeLaw7bhSbDH7CrazT3EV2G72FXzKcoDwn5p9BTksF+idY/YV5LI9QOZcs69gD9sP1N1j9hXsZ3lA"
                + "1P3UV0DdM35z8ftceP7G8v0W4PcF8OSzo34J+D0BnqPsC7838fub8HzBvvRbjN8Xw/Mlzl8yfqe+ggep4wV2AHBIwaNQ2FDcb50x"
                + "O17OYJSwErPjpQSjjJWZHS9lGCpTzY4XFaOclZsdL+UYlazS7HipxLjELpkdL5cwrrArZsfLFYxr7JrZ8XINo4bVmB0vNRiccbPj"
                + "hWPcZDfNjpebGHWsDtfrWNhYXJsbzY6XRow77I7Z8XIHw2CG2fFiYPyT/dPsePknRgfrMDteOjC8zGt2vHgxfsNVjDpefsMQuEJR"
                + "x4vAVcYfP/Nw9fDHCMDPQlwZAjCC8LMIqB+E8QB+EoHoD2BY8EMdLxaMB//F82Qkkr42Y4+upVvSV5MqN2M9fzgtOv0Wj06blf4d"
                + "HnvSvyWFbsFUmllwBzOlgtVcTptYnAf/RyXj4DlRXAkPv6BxNWN2+Uxd27T1wq9czjhZrXB1y9wrsq5tzam+xuUtih7G1W0b9RRd"
                + "+6RMn8Dlbe46D1c/VepH61p2j3p/Lu8Kab7H1dzm2zZd++yZ28Fc3v28J4KrBzo9UbqW9xfPE1w+GN+RwNX80o54XTsyrGMhl/8+"
                + "qnsTV7+M6F6sayfTutdz+csddzO5WijdjdO1bxLuruVyYYp4natlsWKArp3NEM9zmXgerlbuEwd07eJ5Uc9l+l6W9LhCIpWt2K1r"
                + "YrdQSH0rCnm0KBRJfJhwCzdsUtVYmnPpVcw5UJXGZVF/9gpmvq4OgWfAuXHwPP/NPq7ezfy2Tdfuxn37Jy7fXXsS57N709eDdK17"
                + "8Vc2LnevP/IyVzsSjh7VtY74I6e43LEwL42rnojPua55ovJuc9nzxGfvcrX53p4Lunbb9pnO5dvB2dO4WufJ2aFr9aOz87lc77+9"
                + "P1f1sB2v6Jqesn0Fl/UJW3/marWSZde1K3LmNC5XX9vUzNUL2pZhulY+c3Msly/8mjGAq8V5mybrWsm4jGQuF1dSJhRMzdinawV3"
                + "0i9yuWB1KaP/J+KS6L+QFDfrWtFsVxz9txFXMI8uXlOi8IeL33P1hn1Um45nZ2kGzdeKcLRRej48udwBj8r7wrPsZruuFS+pXYqj"
                + "7amdzuWS2IYQvHtZwzquukTDAi67DjYF6VppXtMsrFdDUwxWKq3lOFZNtCB+dWqLxuVzjb8IXTtfYSCjykONIfgsSiti+86vtQtr"
                + "GtfaxuXvWtsTdK0qqz2eq5dutb/G5Usfdi7A2UjsjOHq1eJOnJOrc7pqdE1b23WAqz9c7jrB5R+Wes/iHJZ4cR6uB3mzuMwPdyOH"
                + "a0d6i7jqnuHF+a/10uOuqu4wrGNYVxUpbr3X+TDvUe9RHm3aYd7r3ndJoev+B+bIdTdpTl0Gl7369Rd1zXv2Rg48q2844Mmqseha"
                + "V82PC3HMAzX3uNx14mqXrnUuuDaTq50x37dwuXPa5Yd1rT2hGlG1x1f343L7a5Wf6Fpr88WfuNradXEDl1vbyufo2i+i4ghXjbAK"
                + "fDpjiJqlay3Hz6M2W5rPr+Jyi3Z2vK41BZ1bydWmWecGcbkpphQ50xBSlsXVhnVlyNuGBSW/6trN9lLUXe1S1xV83uklqFA934UV"
                + "4Q4XwznpW3wL5206ZYVmlCBvtaKMRFLZpq8mJW7GHuJ50r8l9W36LZFLeCJOpXky1sNGF9whnqdgKs0vWC2UtIkl44jnKc7D/I+K"
                + "K+Hh5TOJ57mgCSlj9oVfhZJx8opMPE+1IqQtc6uvCQV4kkI8jx4mJKDKBKFsc9ePJp6nziMkoIq/UHaF3LYRz9N8T0hAlWChAE+i"
                + "iOfxRAgJqPKEUIAn8cTzdCQICaiyUCjAk8XE83RvEhJQZb1QgCdxxPPczRQSUGWtUIAnA4jnEa8LCajyvFDKDooD/8nzCAmoUi8U"
                + "whNCE7Gb0MRU5jLgx2mRBCzJJTzBY9rJWRLKpVeJ56kaS3Oq0uCrV4cQz3P2Cp59/dw4eJ7/to14nm/2CQmo8ieh3F379SDieU7i"
                + "PHdv+somlO71R48Sz3PkZSF1JBw5JZSOhZ9z4nny0oTkici7LRTPE3suEM/z2btCar73mS6U28E5O4jnyZ4mpDpPdr7AudvxCvE8"
                + "2/sLSQ/bvkIo+oQsO/E8W38WUrWSOU0o1de2DCOeZ1OzkC5om2OFcuHXTZOJ58kYIKTivIxkoRRXZuwjnoeyomBq+kWhEJ7Qf58p"
                + "biaexyXR/6xx9Ranih91BYtcwhM8fs8VR/+5RjNopjadeB6tCEcbxR3wqHo+8Ty8LzzLapfiCHtuthPPUztdKMCTdUICkoQQz9Ow"
                + "QCjAk1lYo4amIOJ5mmKwUmktiBlIcpx4nhZNKOcaDWRReegvgngeYwg+i9LahXWMa20mnqe1TSjAk3ghAUkSiOdpfw1r9mFnjJCA"
                + "JAuI5+nEOQGeHBASkKSGeJ6uE0IBnuCzA0nOEs/jzcL15LC3SEjuGd0y8TxenH/CE9LjdlWRyrY7jHge77ukvvUeFbmEJ+I08OQ6"
                + "7NG6mzTH/Q/ieeoyhOLVb+TAs/r6i8Tz3HDAk/XjQhztQI2FeJ6ae0LpOnFtppA6Y652Ec/zfYtQOqdVI6r2+MsPE89T3U8o7a9d"
                + "/ElIrV2VnxDPc3GDUFrbKo4IyQgrn0M8TwU+nTHkPOqxpVnNIp7n/CqhtGjnVgqpadbZ8cTznBsklKaYsiwhNawrfYV4njLkbcOC"
                + "UtRa7dKSX4nncV3B553uwopwR8lo4nlcDOekL2WCZhTfIp6nBHnr43l8PI+P5/HxPD6ex8fz+HgeH8/j43l8PI+P5/HxPD6ex8fz"
                + "+HgeH8/j43n+K57nX3q9KMMiFX8wUiJrVWFVebtFKtkTst4iucTAwxaprKHfQIukTu2rW6Ty0D5BFqkyrtfnFunSraAzFulqcc+5"
                + "FumHy4HpFul6UOAki+SeQdZbFDqc7PBE2NVheyxS1wH7cYvUGROOme3xY0MsUmvXuJctkhH2lN0itTQ7NlukplnPLLdIDesmXLZI"
                + "tUuj8O7cQRFqRpTBGMVJ1qrCIk7GKE7GKE7GKE7GKE7GKE7GKE7GKE7GKE7GKE7GKE7GKE5S6oUOJzs8ERZxMkZxMkZxMkZxMkZx"
                + "MkZxMkZxMkZxMkZxMkZxMkZxMqYZpu7Mz1SZ9TC/oQowFWQ9Tb1YsKkOe8DUgvUxlV99TZ3Xg6aqq5+p4RpgKrYGmfqsIaYai75v"
                + "8DOPFmAeJ9g8Qh/ztQ+arxpgzh9izuxhznnAfLaf6R9qevri0TBTDRdparueNJVf48zvwx43VWNjTE3ZY6bibLT5DcEoU6020tSy"
                + "jTC/k7CZOrhHTJXcw+a3IMNwPDraOPM4Y8wjjDZfO9J8lc2c/7A580lzzmPmsyNM/zDTMwqPmKk1Y6am0M/U7vmZ6kJ/U7vnb6oM"
                + "e5javR6m3vBPpnbvT6buMMDU7gWYCsRAU7sXaCoRe5ravZ6mJjHI1O4FmdrEYFO7F2yqFHuZ2r1eplrxAVO794CpW+xtavd6m/rF"
                + "PqZ2r4+pZLSY2j2LqWjsa2r3+praRsnU7kmmxvFBU7v3oKl2fMjU7j1kqh77mdq9fqb+sb+p3etv6iAHmNq9AaYicqCp3RtofnM5"
                + "yNTuDTK/pxxsavcGm1rJIaZ2b4ipmgwxtXshpnpyqKndG0o6yn/Vt0vyGMUfaAZZ7oBVa5d6jJI9Des8hks0zfIYZQ0tzR5DnWqE"
                + "eYzy0NYuj1EZ1x7vMS7d6ozxGFeLuw54jB8ue1d7jOtB3iKP4Z5B1ltUd5PsjRzY1T8u9BhdB67N9BidMdWY2R5/8SeP0dpVccRj"
                + "GGHnNY/R0nxupcdomlWW5TEa1pVGeIzapS68O3dQhJrhMitbM6ucO6i+a5dSfTeso/pumkX13dJM9W2EUX23dlF9t8dTfXfGUH13"
                + "HaD69q6m+ialPcVJlV13k+yNHKrvHxdSfV+bSfVdvZrq++JPVN8VR6i+z2tU3+dWUn2XZVF9l0ZQfbtmUX1ThL769tX3H6O+/w/s"
                + "GcbT"
    };
    /** One mesh of the model. */
    private static final class Part {
        float red, green, blue;
        FloatBuffer positions;
        ShortBuffer index;
        int count;
    }

    private static List<Part> parts;

    private static synchronized List<Part> parts() {
        if (parts != null) return parts;
        List<Part> out = new ArrayList<Part>();
        try {
            StringBuilder all = new StringBuilder();
            for (String piece : DATA) all.append(piece);
            byte[] packed = Base64.decode(all.toString(), Base64.DEFAULT);
            DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(packed)));
            int meshes = in.readUnsignedByte();
            for (int m = 0; m < meshes; m++) {
                Part part = new Part();
                in.readUnsignedByte();
                part.red = in.readUnsignedByte() / 255f;
                part.green = in.readUnsignedByte() / 255f;
                part.blue = in.readUnsignedByte() / 255f;
                int verts = in.readUnsignedShort();
                int indices = in.readUnsignedShort();
                float[] xyz = new float[verts * 3];
                for (int i = 0; i < verts; i++) {
                    // the model's centre is a little to the right of where it is placed
                    xyz[i * 3] = in.readShort() / 30000f - 0.01f;
                    xyz[i * 3 + 1] = in.readShort() / 30000f;
                    xyz[i * 3 + 2] = in.readShort() / 30000f;
                }
                short[] index = new short[indices];
                for (int i = 0; i < indices; i++) index[i] = (short) in.readUnsignedShort();
                part.positions = ByteBuffer.allocateDirect(xyz.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                part.positions.put(xyz).position(0);
                part.index = ByteBuffer.allocateDirect(indices * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
                part.index.put(index).position(0);
                part.count = indices;
                out.add(part);
            }
        } catch (Throwable error) {
            Diary.detail("logo3d: " + error);
            out.clear();
        }
        parts = out;
        return out;
    }

    // ----------------------------------------------------------- the state of it

    /** How far it is turned, and how fast it is turning; written by the finger, read by the drawing. */
    private final Object lock = new Object();
    private float yaw = -3.3f, pitch = 0.5f, spinYaw, spinPitch;
    private boolean touching;
    private long released;
    private float downX, downY;
    private Thread drawing;

    Logo3D(Context context) {
        super(context);
        setOpaque(false);
        setSurfaceTextureListener(this);
        setContentDescription("LotusTok");
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = Math.round(getResources().getDisplayMetrics().density * 320f);
        setMeasuredDimension(resolveSize(0, widthSpec), height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // a page that scrolls would take the drag
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                synchronized (lock) {
                    touching = true;
                    spinYaw = 0f;
                    spinPitch = 0f;
                }
                downX = event.getX();
                downY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - downX, dy = event.getY() - downY;
                downX = event.getX();
                downY = event.getY();
                float scale = 0.011f;
                synchronized (lock) {
                    yaw += dx * scale;
                    pitch += dy * scale;
                    // what the finger did in the last frame, for what is left of it when it lets go
                    spinYaw = spinYaw * 0.4f + (dx * scale / 0.016f) * 0.6f;
                    spinPitch = spinPitch * 0.4f + (dy * scale / 0.016f) * 0.6f;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                synchronized (lock) {
                    touching = false;
                    released = android.os.SystemClock.uptimeMillis();
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    // ------------------------------------------------------------ the surface

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        stopDrawing();
        final SurfaceTexture target = surface;
        final int w = width, h = height;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                draw(target, w, h);
            }
        }, "logo3d");
        drawing = thread;
        thread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // the drawing reads the size of the view as it goes
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        stopDrawing();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void stopDrawing() {
        Thread thread = drawing;
        drawing = null;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final String VERTEX = "uniform mat4 uMatrix; attribute vec3 aPosition; void main() { gl_Position = uMatrix * vec4(aPosition, 1.0); }";
    private static final String FRAGMENT = "precision mediump float; uniform vec4 uColour; void main() { gl_FragColor = uColour; }";

    private static int shader(int type, String source) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, source);
        GLES20.glCompileShader(id);
        return id;
    }

    /** The drawing thread: sets up OpenGL on the view's surface, and draws until it is stopped. */
    private void draw(SurfaceTexture surface, int width, int height) {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface window = EGL14.EGL_NO_SURFACE;
        try {
            int[] version = new int[2];
            EGL14.eglInitialize(display, version, 0, version, 1);
            EGLConfig[] configs = new EGLConfig[1];
            int[] found = new int[1];
            int[] smooth = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, 4, EGL14.EGL_NONE};
            int[] plain = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE};
            EGL14.eglChooseConfig(display, smooth, 0, configs, 0, 1, found, 0);
            if (found[0] == 0) EGL14.eglChooseConfig(display, plain, 0, configs, 0, 1, found, 0);
            if (found[0] == 0) return;
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface, new int[] {EGL14.EGL_NONE}, 0);
            if (!EGL14.eglMakeCurrent(display, window, window, context)) return;

            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, VERTEX));
            GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT));
            GLES20.glLinkProgram(program);
            GLES20.glUseProgram(program);
            int matrixAt = GLES20.glGetUniformLocation(program, "uMatrix");
            int colourAt = GLES20.glGetUniformLocation(program, "uColour");
            int positionAt = GLES20.glGetAttribLocation(program, "aPosition");
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnableVertexAttribArray(positionAt);

            List<Part> model = parts();
            float[] projection = new float[16], view = new float[16], turn = new float[16], matrix = new float[16], scratch = new float[16];
            long born = android.os.SystemClock.uptimeMillis(), last = born;
            while (!Thread.currentThread().isInterrupted()) {
                long now = android.os.SystemClock.uptimeMillis();
                float dt = Math.min(0.05f, (now - last) / 1000f);
                last = now;
                float a, b;
                synchronized (lock) {
                    if (!touching) {
                        yaw += spinYaw * dt;
                        pitch += spinPitch * dt;
                        float keep = (float) Math.exp(-2.6f * dt);
                        spinYaw *= keep;
                        spinPitch *= keep;
                        // after it has stopped it turns back to face front
                        if (released == 0L || now - released > 900L) {
                            float two = (float) (Math.PI * 2);
                            float homeYaw = Math.round(yaw / two) * two, homePitch = Math.round(pitch / two) * two;
                            float pull = 1f - (float) Math.exp(-4.2f * dt);
                            yaw += (homeYaw - yaw) * pull;
                            pitch += (homePitch - pitch) * pull;
                        }
                    }
                    float t = (now - born) / 1000f;
                    a = yaw + (touching ? 0f : (float) Math.sin(t * 1.1f) * 0.32f);
                    b = pitch + (touching ? 0f : (float) Math.sin(t * 0.8f + 1f) * 0.16f) - 0.12f;
                }
                int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
                GLES20.glViewport(0, 0, w, h);
                GLES20.glClearColor(0f, 0f, 0f, 0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                Matrix.perspectiveM(projection, 0, 30f, w / (float) h, 1f, 20f);
                Matrix.setIdentityM(view, 0);
                Matrix.translateM(view, 0, 0f, 0f, -6.2f);
                Matrix.setIdentityM(turn, 0);
                Matrix.rotateM(turn, 0, (float) Math.toDegrees(b), 1f, 0f, 0f);
                Matrix.rotateM(turn, 0, (float) Math.toDegrees(a), 0f, 1f, 0f);
                // the model lies in the x-z plane, with its thickness along y: stood up to face the viewer
                Matrix.rotateM(turn, 0, 90f, 1f, 0f, 0f);
                Matrix.multiplyMM(scratch, 0, view, 0, turn, 0);
                Matrix.multiplyMM(matrix, 0, projection, 0, scratch, 0);
                GLES20.glUniformMatrix4fv(matrixAt, 1, false, matrix, 0);
                for (Part part : model) {
                    GLES20.glUniform4f(colourAt, part.red, part.green, part.blue, 1f);
                    part.positions.position(0);
                    part.index.position(0);
                    GLES20.glVertexAttribPointer(positionAt, 3, GLES20.GL_FLOAT, false, 12, part.positions);
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, part.count, GLES20.GL_UNSIGNED_SHORT, part.index);
                }
                EGL14.eglSwapBuffers(display, window);
                long took = android.os.SystemClock.uptimeMillis() - now;
                if (took < 16L) Thread.sleep(16L - took);
            }
        } catch (InterruptedException ignored) {
            // stopped
        } catch (Throwable error) {
            Diary.detail("logo3d: " + error);
        } finally {
            try {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglTerminate(display);
            } catch (Throwable ignored) {
            }
        }
    }
}
