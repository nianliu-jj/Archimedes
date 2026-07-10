package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.ApiField;

/**
 * 统一响应包装体演示：ResponseBodyAdvice 把 Controller 返回值包进本类。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
public class ResultVo {

    @ApiField("状态码")
    private int code;

    @ApiField("状态信息")
    private String msg;

    @ApiField("业务数据")
    private Object data;

    public ResultVo() {
    }

    public ResultVo(Object data) {
        this.code = 200;
        this.msg = "OK";
        this.data = data;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
