package com.xiaobin.collect;

/**
 * 收集总类
 * url通用格式:    <scheme>://<user>:<password>@<host>:<port>/<path>;<params>?<query>#<frag>
 *
 * http格式:      http://<host>:<port>/<path>?<query>#<frag>
 * https格式:     https://<host>:<port>/<path>?<query>#<frag>
 * mailto格式:    mailto:<RFC-822-addr-spec>,                     例: mailto:joe@joes-hardware.com
 * ftp格式:       ftp://<user>:<password>@<host>:<port>/<path>;<params>
 * rtsp格式:      rtsp://<user>:<password>@<host>:<port>/<path>       通过实时流传输协议解析音/视频资源
 * rtspu格式:     rtspu://<user>:<password>@<host>:<port>/<path>      使用udp方式获取资源
 * file格式:      file://<host>/<path>            如果省略host，则认为是正在使用URL的本机地址
 * news格式:      news:<newsgroud>或news<news-article-id>          例: news:rec.arts.startrek
 * telnet格式:    telnet://<user>:<password>@<host>:<port>/
 *
 * http://www.w3.org/Addressing/        w3c有关URI和URL命名及寻址的WEB页面
 * http://www.ietf.org/rfc/rfc1738      统一资源定为符
 * http://www.ietf.org/rfc/rfc2396.txt  URL: 通用语法
 * http://www.ietf.org/rfc/rfc2141.txt  URN语法
 * http://purl.oclc.org                 永久统一资源定为符的WEB站点
 * http://www.ietf.org/rfc/rfc1808.txt  相对统一资源定为符
 *
 * 100～199  信息提示
 * 200～299  成功
 * 300～399  重定向
 * 400～499  客户端错误
 * 500～599  服务端错误
 */
public abstract class MainCollect {

    /**
     * 启动
     */
    public abstract void start();
}
