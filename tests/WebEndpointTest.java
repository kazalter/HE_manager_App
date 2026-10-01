import com.hemanager.mobile.feature.tools.WebEndpoint;
import com.hemanager.mobile.feature.tools.DownloadNames;
public class WebEndpointTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 static void reject(String value){try{WebEndpoint.normalize(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  check(WebEndpoint.defaultFor("http://192.168.0.101:8010/").equals("http://192.168.0.101:8011"));
  check(WebEndpoint.defaultFor("https://he.example").equals("https://he.example"));
  check(WebEndpoint.sameOrigin("https://he.example","https://HE.example:443/#/tags"));
  check(!WebEndpoint.sameOrigin("https://he.example","http://he.example"));
  check(!WebEndpoint.sameOrigin("http://192.168.0.101:8011","http://192.168.0.101:8010"));
  check(!WebEndpoint.sameOrigin("https://he.example","https://he.example.evil.test"));
  check(!WebEndpoint.sameOrigin("https://he.example","https://he.example@evil.test"));
  check(!WebEndpoint.sameOrigin("https://he.example","javascript:alert(1)"));
  check(WebEndpoint.sameOrigin("http://[::1]:8011","http://[::1]:8011/tags"));
  for(String s:new String[]{"file:///data/data","javascript:alert(1)","http://u:p@host","http://host/#/tags","http://host/path","http://host?token=x","http://host:0","http://host:99999","no url"})reject(s);
  check(DownloadNames.choose("attachment; filename=\"archive.zip\"", "archive.bin").equals("archive.zip"));
  check(DownloadNames.choose("attachment; filename*=UTF-8''%E6%A0%87%E7%AD%BE.json", "download.bin").equals("标签.json"));
  check(DownloadNames.choose("attachment; filename*=UTF-8''a+b.txt", "download.bin").equals("a+b.txt"));
  check(DownloadNames.choose("attachment; filename=\"../../safe.txt\"", "file.bin").equals("safe.txt"));
  check(DownloadNames.choose(null,"file.txt").equals("file.txt"));
  check(DownloadNames.choose("attachment; filename=..", "file.bin").equals("download"));
  System.out.println(checks+" endpoint/security assertions passed");
 }
}
