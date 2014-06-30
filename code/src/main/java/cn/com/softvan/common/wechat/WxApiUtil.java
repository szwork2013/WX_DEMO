package cn.com.softvan.common.wechat;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;

import cn.com.softvan.bean.wechat.TcWxMenuBean;
import cn.com.softvan.bean.wechat.TcWxUserBean;
import cn.com.softvan.common.CommonConstant;
import cn.com.softvan.common.IdUtils;
import cn.com.softvan.common.JedisHelper;
import cn.com.softvan.common.Resources;
import cn.com.softvan.common.Validator;
import cn.com.softvan.common.WebUtils;

/**
 * 
 * @author wuxiaogang
 * 
 */
public final class WxApiUtil {
	/** 日志 */
	private static final transient Logger log = Logger
			.getLogger(WxApiUtil.class);

	/*
	 * 注意事项 上传的多媒体文件有格式和大小限制，如下：  图片（image）: 128K，支持JPG格式 
	 * 语音（voice）：256K，播放长度不超过60s，支持AMRMP3格式  视频（video）：1MB，支持MP4格式 
	 * 缩略图（thumb）：64KB，支持JPG格式
	 * 媒体文件在后台保存时间为3天，即3天后media_id失效。对于需要重复使用的多媒体文件，可以每3天循环上传一次，更新media_id。
	 */
	/**
	 * 调用微信公共平台 多媒体上传接口 上传文件
	 * 
	 * @return
	 */
	public String uploadMedia(String access_token,String msgType, String localFile) {
		String media_id = null;
		String url = "http://file.api.weixin.qq.com/cgi-bin/media/upload?access_token=" + access_token + "&type=" + msgType;
		String local_url = escapeRemoteToLocal(localFile);
		try {
			File file = new File(local_url);
			if (!file.exists() || !file.isFile()) {
				log.error("文件路径错误==" + local_url);
				return null;
			}
			URL urlObj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
			con.setRequestMethod("POST"); // 以Post方式提交表单，默认get方式
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setUseCaches(false); // post方式不能使用缓存
			// 设置请求头信息
			con.setRequestProperty("Connection", "Keep-Alive");
			con.setRequestProperty("Charset", "UTF-8");

			// 设置边界
			String BOUNDARY = "----------" + System.currentTimeMillis();
			con.setRequestProperty("content-type",
					"multipart/form-data; boundary=" + BOUNDARY);
			// con.setRequestProperty("Content-Type",
			// "multipart/mixed; boundary=" + BOUNDARY);
			// con.setRequestProperty("content-type", "text/html");
			// 请求正文信息

			// 第一部分：
			StringBuilder sb = new StringBuilder();
			sb.append("--"); // ////////必须多两道线
			sb.append(BOUNDARY);
			sb.append("\r\n");
			sb.append("Content-Disposition: form-data;name=\"file\";filename=\""
					+ file.getName() + "\"\r\n");
			sb.append("Content-Type:application/octet-stream\r\n\r\n");
			byte[] head = sb.toString().getBytes("utf-8");
			// 获得输出流
			OutputStream out = new DataOutputStream(con.getOutputStream());
			out.write(head);

			// 文件正文部分
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			int bytes = 0;
			byte[] bufferOut = new byte[1024];
			while ((bytes = in.read(bufferOut)) != -1) {
				out.write(bufferOut, 0, bytes);
			}
			in.close();
			// 结尾部分
			byte[] foot = ("\r\n--" + BOUNDARY + "--\r\n").getBytes("utf-8");// 定义最后数据分隔线
			out.write(foot);
			out.flush();
			out.close();
			/**
			 * 读取服务器响应，必须读取,否则提交不成功
			 */
			// con.getResponseCode();
			try {
				// 定义BufferedReader输入流来读取URL的响应
				StringBuffer buffer = new StringBuffer();
				BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
				String line = null;
				while ((line = reader.readLine()) != null) {
					// System.out.println(line);
					buffer.append(line);
				}
				String respStr =buffer.toString();
				log.debug("==respStr==" + respStr);
				try {
					JSONObject dataJson =  JSONObject.fromObject(respStr);
					media_id = dataJson.getString("media_id");
				} catch (Exception e) {
					log.error("==respStr=="+respStr, e);
					try {
						JSONObject dataJson =  JSONObject.fromObject(respStr);
						 return dataJson.getString("errcode");
					} catch (Exception e1) {
					}
				}
			} catch (Exception e) {
				log.error("发送POST请求出现异常！" + e);
			}
		} catch (Exception e) {
			log.error("调用微信多媒体上传接口上传文件失败!文件路径="+local_url);
			log.error("调用微信多媒体上传接口上传文件失败!", e);
		} finally {
		}
		return media_id;
	}
	/**
	 * 调用微信公共平台 下载多媒体文件
	 * 
	 * @return
	 */
	public String downMedia(String access_token,String msgType, String media_id) {
		String localFile = null;
		SimpleDateFormat df = new SimpleDateFormat("/yyyyMM/");
		try {
			String url = "http://file.api.weixin.qq.com/cgi-bin/media/get?access_token=" + access_token + "&media_id=" + media_id;
			//相对路径
			String relative_path="/"+msgType+"/weixin"+df.format(new Date());
//			log.error(path);
			// 图片未保存 下载保存
			URL urlObj = new URL(url);
			HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);
			String xx=conn.getHeaderField("Content-disposition");
			try {
				log.debug("===调用微信公共平台 下载多媒体文件+==返回文件信息=="+xx);
				if(xx==null){
					InputStream in=conn.getInputStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));
				    String line = null;
				    String result = null;
				    while ((line = reader.readLine()) != null) {
				     if(result==null){
				    	 result=line;
				     }else{
				    	 result += line;
				     }
				    }
				    System.out.println(result);
				    JSONObject dataJson =  JSONObject.fromObject(result);
				    return dataJson.getString("errcode");
				}
			} catch (Exception e) {
			}
			if (conn.getResponseCode() == 200) {
				String Content_disposition=conn.getHeaderField("Content-disposition");
				InputStream inputStream = conn.getInputStream();
//				// 文件大小
//				Long fileSize = conn.getContentLengthLong();
				//文件夹 根目录+相对路径
				String savePath = Resources.getData("UPLOAD_ROOT_FOLDER")+relative_path;
				// 文件名
				String fileName = WebUtils.getTime("yyyyMMddHHmmss")+WebUtils.getRandomString(5)+"."+WebUtils.getFileExt(Content_disposition);
				// 创建文件夹
				File saveDirFile = new File(savePath);
				if (!saveDirFile.exists()) {
					saveDirFile.mkdirs();
				}
				// 检查目录写权限
				if (!saveDirFile.canWrite()) {
					log.error("目录没有写权限，写入文件失败");
					throw new Exception();
				}
//				System.out.println("------------------------------------------------");
				// 文件保存目录路径
				File file = new File(saveDirFile, fileName);
				FileOutputStream outStream = new FileOutputStream(file);
				int len = -1;
				byte[] b = new byte[1024];
				while ((len = inputStream.read(b)) != -1) {
					outStream.write(b, 0, len);
				}
				outStream.flush();
				outStream.close();
				inputStream.close();
				//服务器访问路径
				localFile=Resources.getData("UPLOAD_ROOT_FOLDER_URL")+relative_path+fileName;
			}
		} catch (Exception e) {
			log.error("调用微信公共平台 下载多媒体文件失败!", e);
		} finally {
			
		}
		return localFile;
	}

	/** 获取access_token */
	public String getAccessToken(String appid, String secret) {
		String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
				+ appid + "&secret=" + secret;
		
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(url);
		method.getParams().setContentCharset("utf-8");
		// 发送http请求
		String respStr = "";
		try {
			client.executeMethod(method);
			respStr = method.getResponseBodyAsString();
			JSONObject dataJson = JSONObject.fromObject(respStr);
			return dataJson.getString("access_token");
		} catch (Exception e) {
			log.error("获取微信公共账号access_token异常!",e);
		}
		return null;
	}
	/** 获取微信自定义菜单 */
	public List<TcWxMenuBean> getMenu(String access_token) {
		//---------beans-------------
		List<TcWxMenuBean> beans=new ArrayList<TcWxMenuBean>();
		String url = "https://api.weixin.qq.com/cgi-bin/menu/get?access_token=" + access_token;
		log.debug(url);
		
		boolean error_flag=false;
		
		HttpClient client = new HttpClient();
		GetMethod mothod = new GetMethod(url);
		mothod.getParams().setContentCharset("utf-8");
		// 发送http请求
		String respStr = "";
		try {
			client.executeMethod(mothod);
			respStr = mothod.getResponseBodyAsString();
			log.debug("================================="+respStr);
			JSONObject dataJson = JSONObject.fromObject(respStr);
			try {
				if(dataJson!=null && null!=dataJson.getString("errcode")){
					error_flag=true;
					TcWxMenuBean bean=new TcWxMenuBean();
					bean.setErrcode(dataJson.getString("errcode"));
					bean.setErrmsg(dataJson.getString("errmsg"));
					beans.add(bean);
				}
			} catch (Exception e) {
			}
			if(!error_flag){
				log.debug(dataJson);
				//{"menu":{"button":[{"type":"click","name":"今日歌曲","key":"V1001_TODAY_MUSIC","sub_button":[]},{"type":"click","name":"歌手简介","key":"V1001_TODAY_SINGER","sub_button":[]},{"name":"菜单","sub_button":[{"type":"view","name":"搜索","url":"http://www.soso.com/","sub_button":[]},{"type":"view","name":"视频","url":"http://v.qq.com/","sub_button":[]},{"type":"click","name":"赞一下我们","key":"V1001_GOOD","sub_button":[]}]}]}}
				JSONArray jsonArray=(dataJson.getJSONObject("menu").getJSONArray("button"));
				if(jsonArray!=null){
					//1.获取一级菜单
					for(int i=0;i<jsonArray.size();i++){
						String s1=jsonArray.getString(i);
						log.debug("==s1=="+s1);
						JSONObject j1=JSONObject.fromObject(s1);
						String uuid=IdUtils.createUUID(32);
						//TODO--
						TcWxMenuBean bean=new TcWxMenuBean();
						bean.setId(uuid);//主键id
						bean.setMenu_name(j1.getString("name"));
						JSONArray j2Array=j1.getJSONArray("sub_button");
						bean.setBeans(new ArrayList<TcWxMenuBean>());
						bean.setSort_num((i+1)*10);
						//判断是否有子菜单
						if(j2Array!=null && j2Array.size()>0){
							for(int n=0;n<j2Array.size();n++){
								String s2=j2Array.getString(n);
								log.debug("==s2=="+s2);
								JSONObject j2=JSONObject.fromObject(s2);
								//TODO--
								TcWxMenuBean bean2=new TcWxMenuBean();
								bean2.setId(IdUtils.createUUID(32));//主键id
								bean2.setParent_id(bean.getId());//父菜单id
								bean2.setMenu_name(j2.getString("name"));
								try {
									bean2.setMenu_type(j2.getString("type"));
								} catch (Exception e1) {
								}
								try {
									bean2.setMenu_key(j2.getString("key"));
								} catch (Exception e) {
								}
								try {
									bean2.setMenu_url(j2.getString("url"));
								} catch (Exception e) {
								}
								bean2.setSort_num(n+1);
								bean.getBeans().add(bean2);
							}
						}else{
							try {
								bean.setMenu_type(j1.getString("type"));
							} catch (Exception e) {
							}
							try {
								bean.setMenu_key(j1.getString("key"));
							} catch (Exception e) {
							}
							try {
								bean.setMenu_url(j1.getString("url"));
							} catch (Exception e) {
							}
						}
						//add
						beans.add(bean);
					}
				}
			}
			
		} catch (Exception e) {
			log.error("获取微信公共账号access_token异常!",e);
		}
		return beans;
	}
	/**
	 * 将服务器相对地址转为物理地址
	 * 
	 * @param url
	 * @return
	 */
	public String escapeRemoteToLocal(String url) {
		String local_url = null;
		if (url != null) {
			if (!Validator.isUrl(url)) {
				local_url = url.replaceAll(
						Resources.getData("UPLOAD_ROOT_FOLDER_URL"),
						Resources.getData("UPLOAD_ROOT_FOLDER")).replaceAll(
						"//", "/");
			}
		}
		return local_url;
	}

	/**
	 * 将物理地址转为服务器相对地址
	 * 
	 * @param url
	 * @return
	 */
	public String escapeLocalToRemote(String url) {
		String remote_url = null;
		if (url != null) {
			if (!Validator.isUrl(url)){
				remote_url = url.replaceAll(
						Resources.getData("UPLOAD_ROOT_FOLDER"),
						Resources.getData("UPLOAD_ROOT_FOLDER_URL"))
						.replaceAll("//", "/");
			}
		}
		return remote_url;
	}
	/**
	 * 公众号可通过本接口来获取帐号的关注者列表，关注者列表由一串OpenID（加密后的微信号，每个用户对每个公众号的OpenID是唯一的）组成。一次拉取调用最多拉取10000个关注者的OpenID，可以通过多次拉取的方式来满足需求。
	 * @param access_token
	 * @param next_openid 第一个拉取的OPENID，不填默认从头开始拉取
	 * @return
	 */
	public List<TcWxUserBean> getUserList(String access_token,String next_openid){
	    List<TcWxUserBean>  beans=new ArrayList<TcWxUserBean>();
		String url="https://api.weixin.qq.com/cgi-bin/user/get?access_token="+access_token;
		if(Validator.notEmpty(next_openid)){
			url+="&next_openid="+next_openid;
		}
		boolean error_flag=false;
		/*
		 * total	 关注该公众账号的总用户数
		 * count	 拉取的OPENID个数，最大值为10000
		 * data	            列表数据，OPENID的列表
		 * next_openid	 拉取列表的后一个用户的OPENID
		 */
		
		HttpClient client = new HttpClient();
		GetMethod mothod = new GetMethod(url);
		mothod.getParams().setContentCharset("utf-8");
		// 发送http请求
		String respStr = "";
		try {
			client.executeMethod(mothod);
			respStr = mothod.getResponseBodyAsString();
			log.debug(respStr);
			JSONObject dataJson = JSONObject.fromObject(respStr);
			try {
				if(dataJson!=null && null!=dataJson.getString("errcode")){
					error_flag=true;
					TcWxUserBean bean=new TcWxUserBean();
					bean.setErrcode(dataJson.getString("errcode"));
					bean.setErrmsg(dataJson.getString("errmsg"));
					beans.add(bean);
				}
			} catch (Exception e) {
			}
			if(!error_flag){
				log.debug(dataJson);
				Long total=dataJson.getLong("total");
				Integer count=dataJson.getInt("count");
				String next_oid=dataJson.getString("next_openid");
				log.debug("=====next_oid==="+next_oid+"===2==");
				JSONObject data=dataJson.getJSONObject("data");
				JSONArray array=data.getJSONArray("openid");
				Object[] oIdArray=array.toArray();
				if(oIdArray!=null){
					TcWxUserBean bean=null;
					for(Object oid:oIdArray){
						bean=getUser(access_token,oid.toString());
						if(bean!=null){
							bean.setTotal(total);
							bean.setCount(count);
							bean.setNext_openid(next_oid);
							//add
							beans.add(bean);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("微信公共账号 获取用户列表时异常", e);
		}
		return beans;
	}
	/**
	 *	在关注者与公众号产生消息交互后，公众号可获得关注者的OpenID（加密后的微信号，每个用户对每个公众号的OpenID是唯一的。对于不同公众号，同一用户的openid不同）。公众号可通过本接口来根据OpenID获取用户基本信息，包括昵称、头像、性别、所在城市、语言和关注时间。	 * @param access_token
	 * @param openid
	 * @return
	 */
	public TcWxUserBean getUser(String access_token,String openid){
	    TcWxUserBean  bean=new TcWxUserBean();
	    bean.setOpenid(openid);
		String url="https://api.weixin.qq.com/cgi-bin/user/info?access_token="+access_token+"&openid="+openid+"&lang=zh_CN";
		/*
		 * access_token	 是	 调用接口凭证
			openid	 是	 普通用户的标识，对当前公众号唯一
			lang	 否	 返回国家地区语言版本，zh_CN 简体，zh_TW 繁体，en 英语
		 */
		
		HttpClient client = new HttpClient();
		GetMethod mothod = new GetMethod(url);
		mothod.getParams().setContentCharset("utf-8");
		// 发送http请求
		String respStr = "";
		boolean error_flag=false;
		try {
			client.executeMethod(mothod);
			respStr = mothod.getResponseBodyAsString();
			JSONObject dataJson = JSONObject.fromObject(respStr);
			try {
				if(dataJson!=null && null!=dataJson.getString("errcode")){
					error_flag=true;
					bean.setErrcode(dataJson.getString("errcode"));
					bean.setErrmsg(dataJson.getString("errmsg"));
				}
			} catch (Exception e) {
			}
			if(!error_flag){
	//			subscribe	 用户是否订阅该公众号标识，值为0时，代表此用户没有关注该公众号，拉取不到其余信息。
				bean.setSubscribe(dataJson.getString("subscribe"));
				//			openid	 用户的标识，对当前公众号唯一
				//=-------------
	//			nickname	 用户的昵称
				bean.setNickname(dataJson.getString("nickname"));
	//			sex	 用户的性别，值为1时是男性，值为2时是女性，值为0时是未知
				bean.setSex(dataJson.getString("sex"));
	//			city	 用户所在城市
				bean.setCity(dataJson.getString("city"));
	//			country	 用户所在国家
				bean.setCountry(dataJson.getString("country"));
	//			province	 用户所在省份
				bean.setProvince(dataJson.getString("province"));
	//			language	 用户的语言，简体中文为zh_CN
				bean.setLanguage(dataJson.getString("language"));
	//			headimgurl	 用户头像，最后一个数值代表正方形头像大小（有0、46、64、96、132数值可选，0代表640*640正方形头像），用户没有头像时该项为空
				bean.setHeadimgurl(dataJson.getString("headimgurl"));
	//			subscribe_time	 用户关注时间，为时间戳。如果用户曾多次关注，则取最后关注时间
				bean.setSubscribe_time(dataJson.getString("subscribe_time"));
			}
		} catch (Exception e) {
			log.error("微信公共账号 获取用户基本信息时异常", e);
		}
		return bean;
	}
	/**
	 * Boolean falg,JedisHelper jedisHelper,String appid, String secret
	 */
	public String  getAccess_token(Boolean falg,JedisHelper jedisHelper,String appid, String secret){
		String access_token=(String) jedisHelper.get(CommonConstant.SESSION_KEY_USER_WECHAT_ACCESS_TOKEN);
		if(null==access_token||falg){
			access_token=new WxApiUtil().getAccessToken(appid, secret);
			//认证信息缓存7100秒
			jedisHelper.set(CommonConstant.SESSION_KEY_USER_WECHAT_ACCESS_TOKEN,access_token,7150);
		}
		return access_token;
	}
	/**
	 * 验证是否认证码问题 认证码有问题返回true  认证码正确返回false
	 * @param errorCode
	 * @return
	 */
	public boolean isErrAccessToken(String errorCode){
		if(errorCode!=null && ("40001".equals(errorCode)||"40014".equals(errorCode)||"42001".equals(errorCode))){
			return true;
		}
		return false;
	}
	/**
	 *	通过POST一个JSON数据包来发送消息给普通用户，在48小时内不限制发送次数。此接口主要用于客服等有人工消息处理环节的功能，方便开发者为用户提供更加优质的服务。
	 * @param access_token	 是	 调用接口凭证
	 * @param openid	            是	 普通用户的标识，对当前公众号唯一
	 * @return
	 */
	public String sendCustomerService(String access_token,String openid,String json){
		String msg="1";
		String url="https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token="+access_token;
		HttpClient client = new HttpClient();
		PostMethod mothod = new PostMethod(url);
		mothod.getParams().setContentCharset("utf-8");
		// 发送http请求
		String respStr = "";
		boolean error_flag=false;
		try {
//			NameValuePair[] data = { new NameValuePair("body",json)};
			mothod.setRequestBody(json);
			client.executeMethod(mothod);
			respStr = mothod.getResponseBodyAsString();
			JSONObject dataJson = JSONObject.fromObject(respStr);
			try {
				if(dataJson!=null && null!=dataJson.getString("errcode")){
					error_flag=true;
					msg=dataJson.getString("errcode");
				}
			} catch (Exception e) {
			}
			if(!error_flag){
				log.debug("信息发送完成!");
			}
		} catch (Exception e) {
			log.error("客服信息发送信息时异常", e);
		}
		return msg;
	}
	// test
	public static void main(String[] args) throws Exception {
		WxApiUtil api=new WxApiUtil();
//		System.out.println(api.escapeRemoteToLocal("/upload/image/1.jpg"));
//		System.out.println(api.escapeLocalToRemote("/home/softvan/data_sys_file/upload/image/1.jpg"));
//		System.out.println(api.uploadMedia("wxd59460d5a4e2ed75","06c171287e906224f948822e819c730b", "image","/upload/image/n2/20140312163435oy3ip.jpg"));
//		System.out.println(api.downMedia("wxd59460d5a4e2ed75", "06c171287e906224f948822e819c730b","video", "lwjcF6XbJmyNVz2vsVaCG9ot8tqn0QgMEOgL-_jea-KNDC09tAgYH_U7vgogliaY"));
//		String json="{\"total\":23000,\"count\":10000,\"data\":{  \"openid\":[    \"OPENID10001\",    \"OPENID10002\",    \"OPENID20000\"  ]},\"next_openid\":\"NEXT_OPENID2\"}";
//		JSONObject dataJson = JSONObject.fromObject(json);
//		String next_id=dataJson.getString("next_openid");
//		System.out.println(next_id);
//		JSONObject data=dataJson.getJSONObject("data");
//		JSONArray array=data.getJSONArray("openid");
//		Object[] openids=array.toArray();
//		if(openids!=null){
//			for(Object o:openids){
//				System.out.println(o.toString());
//			}
//		}
//		List<TcWxUserBean> ss=api.getUserList(api.getAccessToken("wxd59460d5a4e2ed75", "06c171287e906224f948822e819c730b"),null);
//		if(ss!=null){
//			for(TcWxUserBean s:ss){
//				System.out.println(s.getOpenid());
//			}
//		}
//		TcWxUserBean  bean=api.getUser(api.getAccessToken("wxd59460d5a4e2ed75", "06c171287e906224f948822e819c730b"), "oqnjojk9gd7QhTkl2mDvfm2nsJis");
//		System.out.println(bean.getNickname());
//		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
//		System.out.println(sdf.format(1385216461));
//		String url = "http://file.api.weixin.qq.com/cgi-bin/media/get?access_token=access_token&media_id=media_id";
//		// 图片未保存 下载保存
//		URL urlObj = new URL(url);
//		HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
//		conn.setRequestMethod("GET");
//		conn.setConnectTimeout(5000);
//		
//		InputStream in=conn.getInputStream();
//		BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));
//	    String line = null;
//	    String result = null;
//	    while ((line = reader.readLine()) != null) {
//	     if(result==null){
//	    	 result=line;
//	     }else{
//	    	 result += line;
//	     }
//	    }
//	    System.out.println(result);
		
//		System.out.println(api.uploadMedia("xxxxx","image", "D:/Pictures/2009131731654_2.jpg"));
//		System.out.println(Validator.isUrl("http://www.xx.com"));
	}
}
