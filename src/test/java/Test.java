import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {

    public static void main(String[] args) throws IOException {
        Test test = new Test();
        test.testT();
    }

    private void testT() throws IOException {
        String string = "\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Language\" content=\"zh-cn\">\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=gb2312\">\n" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\">\n" +
                "<meta name=\"keywords\" content=\"在线网络汉语汉字典，新华字典\" />\n" +
                "<meta name=\"description\" content=\"最大最全的在线汉语字典\" />\n" +
                "<title>在线新华字典</title>\n" +
                "</head>\n" +
                "<body topmargin=\"2\" leftmargin=\"0\">\n" +
                "<br>\n" +
                "<p align=center><img border=\"0\" src=\"images/logo.jpg\" alt=\"在线新华字典\"></p>\n" +
                "      <TABLE cellSpacing=0 cellPadding=0 align=center>\n" +
                "        <TBODY>\n" +
                "        <TR>\n" +
                "          <TD height=1></TD></TR>\n" +
                "        <TR>\n" +
                "          <TD><IMG src=\"images/rebig.gif\"></TD>\n" +
                "          <TD>\n" +
                "            <TABLE cellSpacing=0 cellPadding=3 align=center \n" +
                "            background=images/bgserch01.gif border=0>\n" +
                "              <TBODY>\n" +
                "              <TR>\n" +
                "                <FORM action=\"index.php\" method=post>\n" +
                "                <TD vAlign=top noWrap height=45>\n" +
                "                  <CENTER><INPUT size=20 name=f_key style=\"font-size:16px;\">\n" +
                "  <select name=\"f_type\" style=\"font-size:16px;\">\n" +
                "    <option value=\"zi\" selected>汉字</option>\n" +
                "  </select> \n" +
                "                  <INPUT style=\"POSITION: relative; TOP: 5px\" \n" +
                "                  type=image src=\"images/serch.gif\" \n" +
                "                  name=SearchString>&nbsp;&nbsp;<a href=bs.html>按部首检索</a>&nbsp;&nbsp;<a href=pinyi.html>按拼音检索</a>\n" +
                "                </CENTER></TD></FORM></TR></TBODY></TABLE></TD>\n" +
                "          <TD><IMG src=\"images/bgserch02.gif\"></TD></TR>\n" +
                "        <TR>\n" +
                "          <TD height=6></TD></TR></TBODY></TABLE>\n" +
                "\n" +
                "<table cellSpacing=0 cols=3 cellPadding=3 width=758 align=center border=0>\n" +
                "  <tr><td colSpan=3 height=8><img height=8 src=\"images/flzyline.gif\" width=758></td></tr></table>\n" +
                "<table border=0 width=760 align=center><tr><td class=font_19><p >&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;在上面的搜索框内输入条件，点击检索，就可以找到相应汉字的拼音、部首、笔划、注解、出处，也可以通过笔划、部首去检索。一些淘汰不用、电脑输不出的汉字，请通过<a  href=/kxbs.html>在线康熙字典</a>查找,给孩子起名，请通过<a href=/xm/>在线起名大全</a>进行起名字。\n" +
                "</p></td></tr>\n" +
                "</table>\n" +
                "\t<table border=0 width=765 cellspacing=0 cellpadding=0 align=center>\n" +
                "\t\t<tr>\n" +
                "\t\t\t<td height=25><b>热门搜索</b>：<a href=/html3/9151.html >��</a>&nbsp;<a href=/html3/9152.html >��</a>&nbsp;<a href=/html3/9153.html >�L</a>&nbsp;<a href=/html3/9154.html >瀑</a>&nbsp;<a href=/html3/9155.html >�d</a>&nbsp;<a href=/html3/9156.html >�e</a>&nbsp;<a href=/html3/9157.html >�U</a>&nbsp;<a href=/html3/9158.html >�k</a>&nbsp;<a href=/html3/9159.html >�^</a>&nbsp;<a href=/html3/9160.html >�z</a>&nbsp;<a href=/html3/9161.html >�V</a>&nbsp;&nbsp;&nbsp;<b>最新收录</b>：<a href=/html5/301302.html>劳苦功高</a>&nbsp;<a href=/html5/21308.html>债台高筑</a>&nbsp;<a href=/html5/43165.html>阴谋</a>&nbsp;<a href=/html5/z25m37j37580.html>遭受</a>&nbsp;<a href=/html5/z48m69j372696.html>平平整整</a>&nbsp;&nbsp;</td>\n" +
                "\t\t</tr>\n" +
                "\t</table>\n" +
                "<div align=center>\n" +
                "\t<table border=0 width=765 cellspacing=0 cellpadding=0 class=font_19>\n" +
                "\t\t<tr>\n" +
                "\t\t\t<td width=75 valign=top><b><a href=/page/z7875m5667j20449.html>快捷查找</a></b>：</td>\n" +
                "\t\t\t<td><a href=/page/z7152m3481j18801.html>形容词</a>&nbsp;<a href=/page/z7668m4418j19061.html>动词</a>&nbsp;<a href=/page/z1007m5791j18544.html>虚词</a>&nbsp;<a href=/page/z4029m5260j18546.html>数词</a>&nbsp;<a href=/page/z2190m2907j18579.html>代词</a>&nbsp;<a href=/page/z7344m2500j18585.html>叹词</a>&nbsp;<a href=/page/z3033m2847j20016.html>语气词</a>&nbsp;<a href=/page/z7949m2560j18586.html>量词</a>&nbsp;<a href=/page/z7501m8716j18595.html>连词</a>&nbsp;<a href=/page/z2155m1104j18596.html>象声词</a>&nbsp;<a href=/page/z4851m9112j18603.html>助词</a>&nbsp;<a href=/page/z5079m4992j18622.html>副词</a>&nbsp;<a href=/page/z3033m4876j18636.html>介词</a>&nbsp;<a href=/page/z9084m6671j19732.html>方位词</a>&nbsp;<a href=/page/z9361m8613j19774.html>时间词</a><br>\n" +
                "\t\t\t<a href=/bh.html>笔画检索</a>&nbsp;<a href=/stru.html>汉字结构</a>&nbsp;<a href=/page/z8384m1717j18617.html>通假字</a>&nbsp;<a href=/page/z4745m2559j18770.html>生僻字</a>&nbsp;<a href=/page/z7272m6750j18966.html>会意字</a>&nbsp;<a href=/page/z1268m4359j18968.html>象形字</a>&nbsp;<a href=/page/z9907m3552j18976.html>形声字</a>&nbsp;<a href=/page/z1324m2822j19188.html>谦词、敬词</a>&nbsp;<a href=/page/z9373m8243j18866.html>关联词</a>&nbsp;<a href=/page/z2137m9645j19351.html>异形词</a>&nbsp;<a href=/page/z5236m9179j19350.html>异体字</a></td>\n" +
                "\t\t</tr>\n" +
                "\t</table>\n" +
                "</div>\n" +
                "<br>\n" +
                "<table border='0' width='650' align='center' cellpadding='3' bgcolor='#D6DBE7' cellspacing='1'>\n" +
                "<tr bgcolor='#F6F6F6'>\n" +
                "<td ><b><span class=table4>汉语实用附录</span></b></td>\n" +
                "<td width='80%' align=right><a href=/html2/7921.html>实用附录</a> | <a href=/html2/7944.html>汉字启蒙</a> | <a href=/html2/7919.html>文学常识</a> | <a href=/html2/7920.html>历年热词</a> | <a href=/html2/7928.html>汉语研究</a> | <a href=/html2/7935.html>朝代顺序表</a>  </td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "\n" +
                "\n" +
                "<table align='center' border='0' width='650' cellpadding='3' bgcolor='#D6DBE7' cellspacing='1'>\n" +
                "<tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z9493m5997j19951.html' target=\"_blank\" >汉字五行属性查询</a></td><td width=50%>&nbsp;<a href='page/z2491m7594j19615.html' target=\"_blank\" >中国姓氏起源大全</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z9836m4888j19601.html' target=\"_blank\" >熟语大全</a></td><td width=50%>&nbsp;<a href='page/z4722m7356j18994.html' target=\"_blank\" >汉语拼音字母表</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z2734m6275j18808.html' target=\"_blank\" >汉字笔顺规则表</a></td><td width=50%>&nbsp;<a href='page/z8338m5798j18550.html' target=\"_blank\" >现代汉语词类表和语法表</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/18466.html' target=\"_blank\" >特殊字符大全</a></td><td width=50%>&nbsp;<a href='page/z4661m7179j20874.html' target=\"_blank\" >《演小儿语》全文阅读</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z2841m2371j20873.html' target=\"_blank\" >九月的别称雅称</a></td><td width=50%>&nbsp;<a href='page/z9086m2300j20870.html' target=\"_blank\" >冬天的别称和雅称</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z7260m9149j20868.html' target=\"_blank\" >形容好人好事的词语</a></td><td width=50%>&nbsp;<a href='page/z3983m7884j20867.html' target=\"_blank\" >古代对雷电的别称雅称</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z6321m8685j20863.html' target=\"_blank\" >形容笨、愚蠢、不聪明的的词语</a></td><td width=50%>&nbsp;<a href='page/z3678m4968j20862.html' target=\"_blank\" >古人对水的别称</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z1880m7277j20860.html' target=\"_blank\" >100个常见多音字</a></td><td width=50%>&nbsp;<a href='page/z8738m8666j20857.html' target=\"_blank\" >八月的别称雅称</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z9436m5506j20853.html' target=\"_blank\" >中国历史上著名的错字故事</a></td><td width=50%>&nbsp;<a href='page/z2604m5977j20848.html' target=\"_blank\" >同时含有近义、反义词的词语</a></td></tr><tr bgcolor=#ffffff ><td width=50%>&nbsp;<a href='page/z3707m1000j20845.html' target=\"_blank\" >形容洪水猛烈的词语</a></td><td width=50%>&nbsp;<a href='page/z3148m5859j20844.html' target=\"_blank\" >三媒六证和三姑六婆指的是什么</a></td></tr>   </table> <br><div align=\"center\">\n" +
                "<center><table border=\"0\" width=\"760\" cellspacing=\"0\" cellpadding=\"0\" >\n" +
                "<tr>\n" +
                "<td align=center>\n" +
                "<br>\n" +
                "<hr>\n" +
                "</td>\n" +
                "<tr>\n" +
                "<td width=\"100%\" >\n" +
                "<div align=\"center\">\n" +
                "<table border=\"0\" width=\"770\" id=\"table4\" cellpadding=\"3\">\n" +
                "<tr>\n" +
                "<td class=table4>\n" +
                "<p align='center'><b>工具导航</b>: \n" +
                "<a href=http://cy.5156edu.com>成语词典</a>\n" +
                "<a href=http://fyc.5156edu.com>反义词查询</a>\n" +
                "<a href=http://jyc.5156edu.com>近义词查询</a>\n" +
                "<a href=http://wyw.5156edu.com>文言文翻译</a>\n" +
                "<a href=http://xhy.5156edu.com>歇后语大全</a>\n" +
                "<a href=http://ts300.5156edu.com>古诗词大全</a>\n" +
                "<a href=http://www.5156edu.com/nl.html>万年历</a>\n" +
                "<a href=/conversion.html>中文转拼音</a>\n" +
                "<a href=http://xh.5156edu.com/jtof.html>简繁转换</a>\n" +
                "<a href=/hxw.html>火星文</a>\n" +
                "<a href=/qwm.php>区位码</a>\n" +
                "<a href=http://www.5156edu.com>语文网</a>\n" +
                "</td>\n" +
                "</tr>\n" +
                "<tr>\n" +
                "<td align=\"center\" class=font_19>\n" +
                "<b><a href=http://xh.5156edu.com/weixin/><font color=red>手机站</font></a> 版权所有 在线汉语字典 新华字典词典 &nbsp;&nbsp;浙ICP备05019169号</b>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</table>\n" +
                "</div><br>\n" +
                "</div>\n" +
                "</td>\n" +
                "</tr>\n" +
                "</table></center>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>er>\n" +
                "<br>\n" +
                "<hr>\n" +
                "</td>\n" +
                "<tr>\n" +
                "<td width=\"100%\" >\n" +
                "<div align=\"center\">\n" +
                "<table border=\"0\" width=\"770\" id=\"table4\" cellpadding=\"3\">\n" +
                "<tr>\n" +
                "<td class=table4>\n" +
                "<p align='center'><b>工具导航</b>: \n" +
                "<a href=http://cy.5156edu.com>成语词典</a>\n" +
                "<a href=http://fyc.5156edu.com>反义词查询</a>\n" +
                "<a href=http://jyc.5156edu.com>近义词查询</a>\n" +
                "<a href=http://wyw.5156edu.com>文言文翻译</a>\n" +
                "<a href=http://xhy.5156edu.com>歇后语大全</a>\n" +
                "<a href=http://ts300.5156edu.com>古诗词大全</a>\n" +
                "<a href=http://www.5156edu.com/nl.html>万年历</a>\n" +
                "<a href=/conversion.html>中文转拼音</a>\n" +
                "<a href=http://xh.5156edu.com/jtof.html>简繁转换</a>\n" +
                "<a href=/hxw.html>火星文</a>\n" +
                "<a href=/qwm.php>区位码</a>\n" +
                "<a href=http://www.5156edu.com>语文网</a>\n" +
                "</td>\n" +
                "</t";
        Pattern pattern = Pattern.compile("<a[^>]+href=['\"]?([^'\"\\s>]+)[^>]*>");
        Matcher matcher = pattern.matcher(string);
        while(matcher.find()){
            System.out.println(matcher.group(1));
        }
    }
}
