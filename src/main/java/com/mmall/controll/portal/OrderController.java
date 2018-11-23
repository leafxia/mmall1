package com.mmall.controll.portal;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.demo.trade.config.Configs;
import com.google.common.collect.Maps;
import com.mmall.common.Const;
import com.mmall.common.ResponseCode;
import com.mmall.common.ServerResponse;
import com.mmall.model.User;
import com.mmall.service.IOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Iterator;
import java.util.Map;

/**
 * @author leaf
 * @create 2018-11-21 12:05
 * 支付订单
 */
@Controller
@RequestMapping("/order/")
public class OrderController {

private  static  final Logger logger=LoggerFactory.getLogger(OrderController.class);


    @Autowired
    private IOrderService iOrderService;

    /**
     * 订单管理
     * 创建订单
     * @param session
     * @param shippingId
     * @return
     */
    @RequestMapping("create.do")
    @ResponseBody
    public ServerResponse create(HttpSession session,Integer shippingId){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }


        return iOrderService.createOrder(user.getId(),shippingId);
    }

    /**
     * 在未付款的情况下取消订单
     * @param session
     * @param orderNo
     * @return
     */
    @RequestMapping("cancel.do")
    @ResponseBody
    public ServerResponse cancel(HttpSession session,Long orderNo){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        return iOrderService.cancel(user.getId(),orderNo);
    }

    /**
     * 获取购物车中已经选择的商品
     * @param session
     * @param orderNo
     * @return
     */
    @RequestMapping("get_order_cart_product.do")
    @ResponseBody
    public ServerResponse getOrderCartProduct(HttpSession session,Long orderNo){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        return iOrderService.getOrderCarProduct(user.getId());
    }



























    /**
     * 订单详情
     * @param session
     * @param orderNo
     * @return
     */
    @RequestMapping("detail.do")
    @ResponseBody
    public ServerResponse detail(HttpSession session,Long orderNo){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        return iOrderService.getOrderDatall(user.getId(),orderNo);
    }

    /**
     * 个人中心查看个人订单
     * @param session
     * @return
     */
    @RequestMapping("list.do")
    @ResponseBody
    public ServerResponse list(HttpSession session, @RequestParam(value = "pageNum",defaultValue = "1") int  pageNum,@RequestParam(value = "pageSize",defaultValue = "10") int pageSize){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        return iOrderService.getOrderList(user.getId(),pageNum,pageSize);
    }
















    /**
     *生成二维码 支付
     * @param session 用户登陆信息
     * @param orderNo 支付订单号
     * @param request
     * @return
     */
    @RequestMapping("pay.do")
    @ResponseBody
    public ServerResponse pay(HttpSession session,Long orderNo, HttpServletRequest request){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        //拿到生成二维码目的地的路径
        String path=request.getSession().getServletContext().getRealPath("upload");

        return iOrderService.pay(orderNo,user.getId(),path);
    }

    /**
     * 支付宝回调
     * @param request
     * @return
     *
     * 将所有的数据都放置到request中  所以需要到 request 去取
     */
    @RequestMapping("alipay_callback.do")
    @ResponseBody
    public Object  alipayCallback(HttpServletRequest request){

        Map<String,String> params= Maps.newHashMap();

        //取出支付宝的所有的数据
           Map  requestParams= request.getParameterMap();
            //遍历   动态的查询requestParams 的k  和 v
        //Iterator iter=requestParams.keySet().iterator(); 迭代器
        //iter.hasNext();是否有下一个
            for(Iterator iter=requestParams.keySet().iterator();iter.hasNext();){
                //取出名字
                String name= (String) iter.next();
                //取出value
                String [] values= (String[]) requestParams.get(name);
                //声明一个空字符串
                String valueStr="";
                for (int i=0;i<values.length;i++){
                    valueStr = (i==values.length -1)? valueStr+ values[i]:valueStr +values[i]+",";
                }
                    params.put(name,valueStr);
            }
            //掉的验签    交易的状态 整体的参数
        logger.info("支付宝回调，sign:{} trade_status:{} 参数:{}",params.get("sign"),params.get("trade_status"),params.toString());
//支付宝文档中说明需要删除掉签名類型    不删除验证通不过
        params.remove("sign_type");
        //
        try {
            //params 自己组装的map 里面包含了支付宝返回的所有信息
            //Configs.getAlipayPublicKey() 支付宝RSA公钥，用于验签支付宝应答
            //Configs.getSignType() 签名类型
            boolean alipayRSACheckedV2=AlipaySignature.rsaCheckV2(params,Configs.getAlipayPublicKey(),"utf-8",Configs.getSignType());
        //alipayRSACheckedV2 默认 false    取反
            if(!alipayRSACheckedV2){
                return  ServerResponse.createByErrorMessage("非法请求，验证不通过");
            }
        } catch (AlipayApiException e) {
            logger.error("支付宝验证回调异常"+e);
            e.printStackTrace();
        }
        //数据验证
        ServerResponse serverResponse = iOrderService.aliCallvack(params);
        if(serverResponse.isSuccess()){
            return Const.AlipayCallback.RESPONSE_SUCCESS;
        }
        return Const.AlipayCallback.RESPONSE_FAIED;
    }

    //

    /**
     *前台轮询查询支付状态
     * @param session
     * @param orderNo
     * @return
     */
    @RequestMapping("query_order_pay_status.do")
    @ResponseBody
    public ServerResponse<Boolean> queryOrderPayStatus(HttpSession session,Long orderNo){
        //判断用户是否登陆
        User user = (User) session.getAttribute(Const.CURRENT_USER);
        //未登录
        if(user==null){
            //强制登陆
            return  ServerResponse.createByErrorCodeMessage(ResponseCode.NEED_LOGIN.getCode(),ResponseCode.ILLEGAL_ARGUMENT.getDesc());
        }
        //拿到生成二维码目的地的路径
     //   String path=request.getSession().getServletContext().getRealPath("upload");

        ServerResponse serverResponse = iOrderService.queryOrderPayStatus(user.getId(), orderNo);
        if(serverResponse.isSuccess()){
            return  ServerResponse.createBySuccess(true);
        }
        return  ServerResponse.createBySuccess(false);
    }

}
