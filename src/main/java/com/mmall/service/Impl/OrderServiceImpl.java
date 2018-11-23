package com.mmall.service.Impl;

import com.alipay.api.AlipayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.demo.trade.config.Configs;
import com.alipay.demo.trade.model.ExtendParams;
import com.alipay.demo.trade.model.GoodsDetail;
import com.alipay.demo.trade.model.builder.AlipayTradePrecreateRequestBuilder;
import com.alipay.demo.trade.model.result.AlipayF2FPrecreateResult;
import com.alipay.demo.trade.service.AlipayTradeService;
import com.alipay.demo.trade.service.impl.AlipayTradeServiceImpl;
import com.alipay.demo.trade.utils.ZxingUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mmall.common.Const;
import com.mmall.common.ServerResponse;
import com.mmall.dao.*;
import com.mmall.model.*;
import com.mmall.service.IOrderService;
import com.mmall.util.BigDecimalUtil;
import com.mmall.util.DateTimeUtil;
import com.mmall.util.FTPUtil;
import com.mmall.util.PropertiesUtil;
import com.mmall.vo.OrderItemVo;
import com.mmall.vo.OrderProductVo;
import com.mmall.vo.OrderVo;
import com.mmall.vo.ShippingVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author leaf
 * @create 2018-11-21 13:39
 */
@Service("iOrderService")
public class OrderServiceImpl implements IOrderService {
    private static AlipayTradeService tradeService;

    static {

        /** 一定要在创建AlipayTradeService之前调用Configs.init()设置默认参数
         *  Configs会读取classpath下的zfbinfo.properties文件配置信息，如果找不到该文件则确认该文件是否在classpath目录
         */
        Configs.init("zfbinfo.properties");

        /** 使用Configs提供的默认参数
         *  AlipayTradeService可以使用单例或者为静态成员对象，不需要反复new
         */
        tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();
    }

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private PayInfoMapper payInfoMapper;
    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ShippingMapper shippingMapper;

    /**
     * 创建订单
     *
     * @param userId
     * @param shippingId
     * @return
     */
    public ServerResponse createOrder(Integer userId, Integer shippingId) {
        //从 购物车中获取数据
        List<Cart> cartList = cartMapper.selectCheckedCartByUserId(userId);
        //计算订单总价
        ServerResponse serverResponse = this.getCartOrderItem(userId, cartList);
//不成功  错误时
        if (!serverResponse.isSuccess()) {
            return serverResponse;
        }
//计算总价
        List<OrderItem> orderItemList = (List<OrderItem>) serverResponse.getData();
        //拿到总价
        BigDecimal payment = this.getOrderTotalPrice(orderItemList);
//生成订单
        Order order = this.assembleOrder(userId, shippingId, payment);
        if (order == null) {
            return serverResponse.createByErrorMessage("生成订单错误");
        }
        if (CollectionUtils.isEmpty(orderItemList)) {
            return serverResponse.createByErrorMessage("购物车为空");
        }

        for (OrderItem orderItem : orderItemList) {
            orderItem.setOrderNo(order.getOrderNo());
        }
        //批量插入
        orderItemMapper.batchInsert(orderItemList);
        //生成成功，减少产品库存
        this.reduceProductStock(orderItemList);
        //清空购物车
        this.cleanCart(cartList);
        //返回给前端数据 订单的明细 时间格式  订单号 状态
        OrderVo orderVo = assembleOrderVo(order, orderItemList);
        return ServerResponse.createBySuccess(orderVo);
    }

    /**
     * 返回一个 包含订单信息 订单明细信息  还有收货地址的信息
     *
     * @param order
     * @param orderItemList
     * @return
     */
    private OrderVo assembleOrderVo(Order order, List<OrderItem> orderItemList) {
        OrderVo orderVo = new OrderVo();
        orderVo.setOrderNo(order.getOrderNo());//订单号
        orderVo.setPayment(order.getPayment());
        orderVo.setPaymentType(order.getPaymentType());
        orderVo.setPaymentTypeDesc(Const.PaymentTypeEnum.codeOf(order.getPaymentType()).getValue());//支付的描述 如 在线支付

        orderVo.setPostage(order.getPostage());
        orderVo.setStatus(order.getStatus());
        orderVo.setStatusDesc(Const.OrderStatusEnum.codeOf(order.getStatus()).getValue());//支付状态的描述 如 未支付

        orderVo.setShippingId(order.getShippingId());
        Shipping shipping = shippingMapper.selectByPrimaryKey(order.getShippingId());
        if (shipping != null) {
            orderVo.setReceiverName(shipping.getReceiverName());
            orderVo.setShippingVo(assembleShippingVo(shipping));
        }

        orderVo.setPaymentTime(DateTimeUtil.dateToStr(order.getPaymentTime()));
        orderVo.setSendTime(DateTimeUtil.dateToStr(order.getSendTime()));
        orderVo.setEndTime(DateTimeUtil.dateToStr(order.getEndTime()));
        orderVo.setCreateTime(DateTimeUtil.dateToStr(order.getCreateTime()));
        orderVo.setCloseTime(DateTimeUtil.dateToStr(order.getCloseTime()));

//图片
        orderVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix"));
        List<OrderItemVo> orderItemVoList = Lists.newArrayList();
        for (OrderItem orderItem : orderItemList) {
            OrderItemVo orderItemVo = assembleOrderItemVo(orderItem);
            orderItemVoList.add(orderItemVo);
        }
        orderVo.setOrderItemVoList(orderItemVoList);
        return orderVo;
    }

    private OrderItemVo assembleOrderItemVo(OrderItem orderItem) {
        OrderItemVo orderItemVo = new OrderItemVo();
        orderItemVo.setOrderNo(orderItem.getOrderNo());
        orderItemVo.setProductId(orderItem.getProductId());
        orderItemVo.setProductName(orderItem.getProductName());
        orderItemVo.setProductImage(orderItem.getProductImage());
        orderItemVo.setCurrentUnitPrice(orderItem.getCurrentUnitPrice());
        orderItemVo.setQuantity(orderItem.getQuantity());
        orderItemVo.setTotalPrice(orderItem.getTotalPrice());

        //时间
        orderItemVo.setCreateTime(DateTimeUtil.dateToStr(orderItem.getCreateTime()));
        return orderItemVo;
    }

    private ShippingVo assembleShippingVo(Shipping shipping) {
        ShippingVo shippingVo = new ShippingVo();
        shippingVo.setReceiverName(shipping.getReceiverName());
        shippingVo.setReceiverAddress(shipping.getReceiverAddress());
        shippingVo.setReceiverProvince(shipping.getReceiverProvince());
        shippingVo.setReceiverCity(shipping.getReceiverCity());
        shippingVo.setReceiverDistrict(shipping.getReceiverDistrict());
        shippingVo.setReceiverMobile(shipping.getReceiverMobile());
        shippingVo.setReceiverZip(shipping.getReceiverZip());
        shippingVo.setReceiverPhone(shipping.getReceiverPhone());
        return shippingVo;
    }

    /**
     * 减少购物车内已经购买的产品的数据
     *
     * @param cartList
     */
    private void cleanCart(List<Cart> cartList) {
        for (Cart cart : cartList) {
            cartMapper.deleteByPrimaryKey(cart.getId());
        }

    }

    /**
     * 减少产品库存
     *
     * @param orderItemList
     */
    private void reduceProductStock(List<OrderItem> orderItemList) {
        for (OrderItem orderItem : orderItemList) {
            Product product = productMapper.selectByPrimaryKey(orderItem.getProductId());
            product.setStock(product.getStock() - orderItem.getQuantity());
            productMapper.updateByPrimaryKeySelective(product);
        }
    }

    /**
     * 生成订单
     *
     * @param userId
     * @param shippingId
     * @param payment
     * @return
     */
    private Order assembleOrder(Integer userId, Integer shippingId, BigDecimal payment) {
        Order order = new Order();

        //订单号
        long orderNo = this.generateOrderNo();
        //生成订单
        order.setOrderNo(orderNo);
        order.setStatus(Const.OrderStatusEnum.NO_PAY.getCode());
        order.setPostage(0);//全场包邮  运费
        order.setPaymentType(Const.PaymentTypeEnum.ONLINE_PAY.getCode());//支付方式
        order.setPayment(payment);

        order.setUserId(userId);
        order.setShippingId(shippingId);
        int rowCount = orderMapper.insert(order);
        if (rowCount > 0) {
            return order;
        }
        return null;
    }

    /**
     * 订单号生成 规则  时间戳取余
     *
     * @return
     */
    private long generateOrderNo() {
        long currentTime = System.currentTimeMillis();
        return currentTime + new Random().nextInt(100);
    }

    /**
     * 计算所有商品的总价
     *
     * @param orderItemList
     * @return
     */
    private BigDecimal getOrderTotalPrice(List<OrderItem> orderItemList) {
        BigDecimal payment = new BigDecimal("0");
        for (OrderItem orderItem : orderItemList) {
            payment = BigDecimalUtil.add(payment.doubleValue(), orderItem.getTotalPrice().doubleValue());
        }
        return payment;
    }


    /**
     * 通过购物车将子订单的明细创建出来
     *
     * @param userId
     * @param cartList
     * @return
     */
    private ServerResponse getCartOrderItem(Integer userId, List<Cart> cartList) {
        List<OrderItem> orderItemList = Lists.newArrayList();
        if (CollectionUtils.isEmpty(cartList)) {
            return ServerResponse.createByErrorMessage("购物车为空");

        }
        //校验购物车的数据包括产品的状态数量
        for (Cart cartItem : cartList) {
            OrderItem orderItem = new OrderItem();
            Product product = productMapper.selectByPrimaryKey(cartItem.getProductId());
            if (Const.ProductStatusEnum.ON_SALE.getCode() != product.getStatus()) {
                return ServerResponse.createByErrorMessage("产品" + product.getName() + "不是在线售卖状态");

            }
            //校验库存 购物车   库存
            if (cartItem.getQuantity() > product.getStock()) {
                return ServerResponse.createByErrorMessage("产品" + product.getName() + "库存不足");
            }

            orderItem.setUserId(userId);
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getMainImage());
            orderItem.setCurrentUnitPrice(product.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setTotalPrice(BigDecimalUtil.mul(product.getPrice().doubleValue(), cartItem.getQuantity()));
            orderItemList.add(orderItem);
        }
        return ServerResponse.createBySuccess(orderItemList);
    }


    /**
     * 返回订单取消成功 或失败
     *
     * @param userId
     * @param orderNo
     * @return
     */
    public ServerResponse<String> cancel(Integer userId, Long orderNo) {
//拿到订单
        Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
        if (order == null) {
            return ServerResponse.createByErrorMessage("该用户此订单不存在");
        }
        //todo 二期
        if (order.getStatus() != Const.OrderStatusEnum.NO_PAY.getCode()) {
            return ServerResponse.createByErrorMessage("已付款，无法取消");
        }
//跟新订单
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setStatus(Const.OrderStatusEnum.CANCELED.getCode());
        int row = orderMapper.updateByPrimaryKeySelective(updateOrder);
        if (row > 0) {
            return ServerResponse.createBySuccess();
        }
        return ServerResponse.createByError();

    }

    /**
     * 获取购物车中已经选择的商品
     *
     * @param userId
     * @return
     */

    public ServerResponse getOrderCarProduct(Integer userId) {
        OrderProductVo orderProductVo = new OrderProductVo();
        //从购物车中获取数据
        List<Cart> cartList = cartMapper.selectCheckedCartByUserId(userId);
        //获取购物车中的item
        ServerResponse serverResponse = this.getCartOrderItem(userId, cartList);
        if (!serverResponse.isSuccess()) {
            return serverResponse;
        }
        //成功计算总价
        List<OrderItem> orderItemList = (List<OrderItem>) serverResponse.getData();

        List<OrderItemVo> orderItemVoList = Lists.newArrayList();
        //计算目前购物车中勾选物品的总价

        //初始化总价
        BigDecimal payment = new BigDecimal("0");

        for (OrderItem orderItem : orderItemList) {
            payment = BigDecimalUtil.add(payment.doubleValue(), orderItem.getTotalPrice().doubleValue());
            orderItemVoList.add(assembleOrderItemVo(orderItem));
        }
        orderProductVo.setProductTotalPrice(payment);
        orderProductVo.setOrderItemVoList(orderItemVoList);
        orderProductVo.setImageHost(PropertiesUtil.getProperty("ftp.server.http.prefix"));
        return ServerResponse.createBySuccess(orderProductVo);
    }

    /**
     * 获取订单详情
     *
     * @param userId
     * @param orderNo
     * @return
     */
    public ServerResponse<OrderVo> getOrderDatall(Integer userId, Long orderNo) {
        Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
        if (order != null) {
            List<OrderItem> orderItemList = orderItemMapper.getByOrderNoUserId(orderNo, userId);
            OrderVo orderVo = assembleOrderVo(order, orderItemList);
            return ServerResponse.createBySuccess(orderVo);
        }

        return ServerResponse.createByErrorMessage("没有找到该订单");
    }

    /**
     * 个人中心查看个人订单
     *
     * @param userId
     * @param pageNum
     * @param pageSize
     * @return
     */
    public ServerResponse<PageInfo> getOrderList(Integer userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Order> orderList = orderMapper.selectByUserId(userId);
        List<OrderVo> orderVoList = assembleOrderVoList(orderList, userId);
        PageInfo pageResult = new PageInfo(orderList);
        pageResult.setList(orderVoList);
        return ServerResponse.createBySuccess(pageResult);

    }

    /**
     * 查订单列表订单详情
     */
    private List<OrderVo> assembleOrderVoList(List<Order> orderList, Integer userId) {
        List<OrderVo> orderVoList = Lists.newArrayList();
        List<OrderItem> orderItemList = Lists.newArrayList();
        for (Order order : orderList) {

            if (userId == null) {
                //管理员查询
                orderItemList = orderItemMapper.getByOrderNo(order.getOrderNo());

            } else {
                //普通用户通道
                orderItemList = orderItemMapper.getByOrderNoUserId(order.getOrderNo(), userId);
            }
            OrderVo orderVo = assembleOrderVo(order, orderItemList);
            orderVoList.add(orderVo);
        }
        return orderVoList;
    }
















    /**
     * @param orderNo 订单号
     * @param userId  登陆用户id
     * @param path    二维码返回的目的路径
     * @return
     */
    public ServerResponse pay(Long orderNo, Integer userId, String path) {
        //与前端的约定   将二维码返回给前端   订单号也返回给前端
        Map<String, String> resultMap = Maps.newHashMap();
        //校验 根据userid和userNo查出来的 roder 订单是否存在
        Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
        /* selectByUserIdAndOrderNo*/
        //order == null 该用户没有该订单 直接返回
        if (order == null) {
            return ServerResponse.createByErrorMessage("用户没有该订单");
        }
        // resultMap.put("orderNo",String.valueOf(order.getOrderNo()));
        //order ！= null 将数据放入map 中String.valueOf(order.getOrderNo()) 通过string转换
        resultMap.put("orderNo", String.valueOf(order.getOrderNo()));
        //组装生成支付宝订单的各种参数
        /*(必填) 商户网站订单系统中唯一订单号，64个字符以内，只能包含字母、数字、下划线，
       需保证商户系统端不能重复，建议通过数据库sequence生成，
        String outTradeNo =   "tradeprecreate" + System.currentTimeMillis() + (long) (Math.random() * 10000000L);
        改成自己项目的订单好 如String outTradeNo = order.getOrderNo().toString();*/

        String outTradeNo = order.getOrderNo().toString();

        /*(必填) 订单标题，粗略描述用户的支付目的。如“xxx品牌xxx门店当面付扫码消费”
         *   String subject = "xxx品牌xxx门店当面付扫码消费";
         *改未自己使用瓶装 String subject = new StringBuilder()
         * .append("happymmall，订单号：").append(outTradeNo).toString();的方法
         *   */
        String subject = new StringBuilder().append("happymmall，订单号：").append(outTradeNo).toString();

        /*(必填) 订单总金额，单位为元，不能超过1亿元
        如果同时传入了【打折金额】,【不可打折金额】,【订单总金额】三者,则必须满足如下条件:【订单总金额】=【打折金额】+【不可打折金额】
         String totalAmount = "0.01"; 考虑到一期没有打折选项
         直接使用order中的数据order.getPayment().toString()

        */
        String totalAmount = order.getPayment().toString();

        /*(可选) 订单不可打折金额，可以配合商家平台配置折扣活动，如果酒水不参与打折，则将对应金额填写至此字段
        如果该值未传入,但传入了【订单总金额】,【打折金额】,则该值默认为【订单总金额】-【打折金额】
       */
        String undiscountableAmount = "0";

        // 卖家支付宝账号ID，用于支持一个签约账号下支持打款到不同的收款账号，(打款到sellerId对应的支付宝账号)
        // 如果该字段为空，则默认为与支付宝签约的商户的PID，也就是appid对应的PID
        String sellerId = "";

        /*订单描述，可以对交易或商品进行一个详细地描述，比如填写"购买商品2件共15.00元"
         *  String body = "购买商品3件共20.00元";
         *  使用 new StringBuilder().append("订单：").append(outTradeNo).
         *  append(" 购买商品共：").
         *  append(totalAmount).append("元").toString()来计算
         * */
        String body = new StringBuilder().append("订单：").append(outTradeNo).append(" 购买商品共：").append(totalAmount).append("元").toString();

        // 商户操作员编号，添加此参数可以为商户操作员做销售统计
        String operatorId = "test_operator_id";

        // (必填) 商户门店编号，通过门店号和商家后台可以配置精准到门店的折扣信息，详询支付宝技术支持
        String storeId = "test_store_id";

        // 业务扩展参数，目前可添加由支付宝分配的系统商编号(通过setSysServiceProviderId方法)，详情请咨询支付宝技术支持
        ExtendParams extendParams = new ExtendParams();
        extendParams.setSysServiceProviderId("2088100200300400500");

        // 支付超时，定义为120分钟
        String timeoutExpress = "120m";

        // 商品明细列表，需填写购买商品详细信息，
        List<GoodsDetail> goodsDetailList = new ArrayList<GoodsDetail>();
        //region
        /*1.查询订单字表  即 交易信息表 中该次交易的所有商品信息
         *List<OrderItem> orderItems =orderItemMapper.getByOrderNoUserId(orderNo,userId);
         * 2.循环遍历 将信息放入支付宝的商品信息中 而后添加到 支付宝的商品明细列表 及List<GoodsDetail> goodsDetailList
         * = new ArrayList<GoodsDetail>();里面
         * for (OrderItem orderItem : orderItemList) { GoodsDetail goods1 = GoodsDetail.newInstance(orderItem.getProductId().toString(), orderItem.getProductName(),BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(), new Double(100).doubleValue()).longValue(), orderItem.getQuantity());goodsDetailList.add(goods1); }
         * * */
        //1 endregion
        List<OrderItem> orderItemList = orderItemMapper.getByOrderNoUserId(orderNo, userId);


        //2 getCurrentUnitPrice 单价  BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(),new Double(100).doubleValue()).longValue()使用乘法计算商品单价并且将分转换成元
        for (OrderItem orderItem : orderItemList) {
            GoodsDetail goods = GoodsDetail.newInstance(orderItem.getProductId().toString(), orderItem.getProductName(),
                    BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(), new Double(100).doubleValue()).longValue(),
                    orderItem.getQuantity());
            goodsDetailList.add(goods);
        }


     /*   // 创建一个商品信息，参数含义分别为商品id（使用国标）、名称、单价（单位为分）、数量，如果需要添加商品类别，详见GoodsDetail
        GoodsDetail goods1 = GoodsDetail.newInstance("goods_id001", "xxx小面包", 1000, 1);
        // 创建好一个商品后添加至商品明细列表
        goodsDetailList.add(goods1);

        // 继续创建并添加第一条商品信息，用户购买的产品为“黑人牙刷”，单价为5.00元，购买了两件
        GoodsDetail goods2 = GoodsDetail.newInstance("goods_id002", "xxx牙刷", 500, 2);
        goodsDetailList.add(goods2);


*/
        // 创建扫码支付请求builder，设置请求参数 支付宝生产
        AlipayTradePrecreateRequestBuilder builder = new AlipayTradePrecreateRequestBuilder()
                .setSubject(subject).setTotalAmount(totalAmount).setOutTradeNo(outTradeNo)
                .setUndiscountableAmount(undiscountableAmount).setSellerId(sellerId).setBody(body)
                .setOperatorId(operatorId).setStoreId(storeId).setExtendParams(extendParams)
                .setTimeoutExpress(timeoutExpress)
                .setNotifyUrl("http://a9ab5fb48c01e678.natapp.cc/order/alipay_callback.do")//支付宝服务器主动通知商户服务器里指定的页面http路径,根据需要设置PropertiesUtil.getProperty("alipay.callback.url")
                .setGoodsDetailList(goodsDetailList);
        /*AlipayTradeService tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();*/
        AlipayF2FPrecreateResult result = tradeService.tradePrecreate(builder);
        switch (result.getTradeStatus()) {
            case SUCCESS:
                logger.info("支付宝预下单成功: )");
// region
                //   AlipayTradePrecreateResponse response = result.getResponse();
// endregion
                AlipayTradePrecreateResponse response = result.getResponse();
// region
                //    dumpResponse(response);
// endregion
                dumpResponse(response);
                /**
                 * 二维码图片路径
                 */
                // region
                //       File folder = new File(path);
                //                //如果该路径不存在
                //                if (!folder.exists()) {
                //                    //生成一个目录gatewayUrl不应该为NULL！
                //                    folder.setWritable(true);
                //                    folder.mkdirs();
                //                }
// endregion
                File folder = new File(path);
                //如果该路径不存在
                if (!folder.exists()) {
                    //生成一个目录gatewayUrl不应该为NULL！
                    folder.setWritable(true);
                    folder.mkdirs();
                }

                /**
                 * 根据外部订单号生成路径， 路径生成二维码
                 * new 一个文件名
                 */
                // 需要修改为运行机器上的路径
                //细节 /  生成二维码的路径qrPath  要生成的文件名qrFileName

                // region
                //  String qrPath = String.format(path + "/qr-%s.png", response.getOutTradeNo());
                //
                //                String qrFileName = String.format("qr-%s.png", response.getOutTradeNo());
                //
                //                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, qrPath);
                //                File targetFile = new File(path, qrFileName);
                //
                //                try {
                //                    FTPUtil.uploadFile(Lists.newArrayList(targetFile));
                //                } catch (IOException e) {
                //                    logger.error("上传二维码异常" + e);
                //                    e.printStackTrace();
                //                }
                //
                //                logger.info("qrPath:" + qrPath);
                //                //                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, filePath);
                //                String qrUrl = PropertiesUtil.getProperty("ftp.server.http.prefix") + targetFile.getName();
                //                //将二维码放入map
                //                resultMap.put("qrUrl", qrUrl);
// endregion
                String qrPath = String.format(path + "/qr-%s.png", response.getOutTradeNo());

                String qrFileName = String.format("qr-%s.png", response.getOutTradeNo());

                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, qrPath);
                File targetFile = new File(path, qrFileName);

                try {
                    FTPUtil.uploadFile(Lists.newArrayList(targetFile));
                } catch (IOException e) {
                    logger.error("上传二维码异常" + e);
                    e.printStackTrace();
                }

                logger.info("qrPath:" + qrPath);
                //                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, filePath);
                String qrUrl = PropertiesUtil.getProperty("ftp.server.http.prefix") + targetFile.getName();
                //将二维码放入map
                resultMap.put("qrUrl", qrUrl);

                return ServerResponse.createBySuccess(resultMap);
            case FAILED:
                logger.error("支付宝预下单失败!!!");
                return ServerResponse.createByErrorMessage("支付宝预下单失败!!!");

            case UNKNOWN:
                logger.error("系统异常，预下单状态未知!!!");
                return ServerResponse.createByErrorMessage("系统异常，预下单状态未知!!!");

            default:
                logger.error("不支持的交易状态，交易返回异常!!!");
                return ServerResponse.createByErrorMessage("不支持的交易状态，交易返回异常!!!");
        }
    }

    // 简单打印应答
    private void dumpResponse(AlipayResponse response) {
        if (response != null) {
            logger.info(String.format("code:%s, msg:%s", response.getCode(), response.getMsg()));
            if (StringUtils.isNotEmpty(response.getSubCode())) {
                logger.info(String.format("subCode:%s, subMsg:%s", response.getSubCode(),
                        response.getSubMsg()));
            }
            logger.info("body:" + response.getBody());
        }
    }

    public ServerResponse aliCallvack(Map<String, String> params) {

        //外部订单号
        // region拿出订单号
//        Long orderNo=Long.parseLong(params.get("out_trade_no"));
        //endregion
        Long orderNo = Long.parseLong(params.get("out_trade_no"));
        // region 支付宝的交易号
        //   String tradeNo=params.get("trade_no");
//endregion
        String tradeNo = params.get("trade_no");
        // region  交易状态
        // String tradeStatus= params.get("trade_status");
        //endregion
        String tradeStatus = params.get("trade_status");
//查询内部订单号
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return ServerResponse.createByErrorMessage("非此商城订单，回调忽略");
        }
        //判断订单的交易状态
        if (order.getStatus() >= Const.OrderStatusEnum.PAID.getCode()) {
            return ServerResponse.createBySuccess("支付宝重复调用");
        }
//region
     /*   if (Const.AlipayCallback.TRADE_STATUS_TRADE_SUCCESS.equals(tradeStatus)) {
            //跟新时间
            order.setPaymentTime(DateTimeUtil.strToDate(params.get("gmt_payment")));
            //跟新订单状态 改成已付款
            order.setStatus(Const.OrderStatusEnum.PAID.getCode());
            orderMapper.updateByPrimaryKeySelective(order);
        }*/
        //endregion
        //判断状态是否交易成功
        if (Const.AlipayCallback.TRADE_STATUS_TRADE_SUCCESS.equals(tradeStatus)) {
            //跟新时间
            order.setPaymentTime(DateTimeUtil.strToDate(params.get("gmt_payment")));
            //跟新订单状态 改成已付款
            order.setStatus(Const.OrderStatusEnum.PAID.getCode());
            orderMapper.updateByPrimaryKeySelective(order);
        }
        //组装payinfo对象// PayInfo payInfo = new PayInfo();
        //        payInfo.setUserId(order.getUserId());
        //        payInfo.setOrderNo(order.getOrderNo());
        PayInfo payInfo = new PayInfo();
        payInfo.setUserId(order.getUserId());
        payInfo.setOrderNo(order.getOrderNo());
        //使用的那个支付工具 payInfo.setPayPlatform(Const.PayPlatformEnum.ALIPAY.getCode());
        payInfo.setPayPlatform(Const.PayPlatformEnum.ALIPAY.getCode());
//支付宝的交易号
        payInfo.setPlatformNumber(tradeNo);// payInfo.setPlatformNumber(tradeNo);
//状态
        payInfo.setPlatformStatus(tradeStatus);//  payInfo.setPlatformStatus(tradeStatus)
        payInfoMapper.insert(payInfo);//   payInfoMapper.insert(payInfo);
        return ServerResponse.createBySuccess();
    }

    /**
     * 返回给前端
     *
     * @param userId
     * @param orderNo
     * @return
     */
    public ServerResponse queryOrderPayStatus(Integer userId, Long orderNo) {
        Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
        if (order == null) {
            return ServerResponse.createByErrorMessage("没有该订单");
        }
        //判断支付状态
        if (order.getStatus() >= Const.OrderStatusEnum.PAID.getCode()) {

            return ServerResponse.createBySuccess();
        }
        return ServerResponse.createByError();
    }


























    //backend

    /**
     * 后台订单页面
     *
     * @param pageNum
     * @param pageSize
     * @return
     */
    public ServerResponse<PageInfo> manageList(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Order> orderList = orderMapper.selectAllOrder();
        List<OrderVo> orderVoList = this.assembleOrderVoList(orderList, null);
        PageInfo pageResult = new PageInfo(orderList);
        pageResult.setList(orderList);
        return ServerResponse.createBySuccess(pageResult);
    }

    /**
     * 查看订单详情
     *
     * @param orderNo
     * @return
     */
    public ServerResponse<OrderVo> manageDetail(Long orderNo) {
//查看订单
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            List<OrderItem> orderItemList = orderItemMapper.getByOrderNo(orderNo);
            OrderVo orderVo = assembleOrderVo(order, orderItemList);
            return ServerResponse.createBySuccess(orderVo);
        }
        return ServerResponse.createByErrorMessage("订单不存在");
    }

    public ServerResponse<PageInfo> manageSearch(Long orderNo, int pageNum, int pageSize) {
//查看订单
        PageHelper.startPage(pageNum, pageSize);
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            List<OrderItem> orderItemList = orderItemMapper.getByOrderNo(orderNo);
            OrderVo orderVo = assembleOrderVo(order, orderItemList);
            PageInfo pageResult = new PageInfo(Lists.newArrayList(order));
            pageResult.setList(Lists.newArrayList(orderVo));
            return ServerResponse.createBySuccess(pageResult);
        }
        return ServerResponse.createByErrorMessage("订单不存在");
    }

    /**
     * 发货
     * @param orderNo
     * @return
     */
    public  ServerResponse<String> manageSendGoods(Long orderNo){
        //查询订单
        Order order = orderMapper.selectByOrderNo(orderNo);
        if(order!=null){
            if(order.getStatus()==Const.OrderStatusEnum.PAID.getCode()){
                order.setStatus(Const.OrderStatusEnum.SHIPPED.getCode());
                order.setSendTime(new Date());
                orderMapper.updateByPrimaryKeySelective(order);
                return ServerResponse.createBySuccess("发货成功");

            }
        }
        return ServerResponse.createByErrorMessage("订单不存在");
    }

}

// region
/*public ServerResponse queryOrderPayStatus(Integer userId, Long orderNo) {
    Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
    if (order == null) {
        return ServerResponse.createByErrorMessage("没有该订单");
    }
    //判断支付状态
    if (order.getStatus() >= Const.OrderStatusEnum.PAID.getCode()) {

        return ServerResponse.createBySuccess();
    }
    return ServerResponse.createByError();
}*/

//endregion


// region  注释
  /*  public ServerResponse pay(Long orderNo, Integer userId, String path) {
        //与前端的约定   将二维码返回给前端   订单号也返回给前端
        Map<String, String> resultMap = Maps.newHashMap();
        //校验 根据userid和userNo查出来的 roder 订单是否存在
        Order order = orderMapper.selectByUserIdAndOrderNo(userId, orderNo);
        //order == null 该用户没有该订单 直接返回
        if (order == null) {
            return ServerResponse.createByErrorMessage("用户没有该订单");
        }
        // resultMap.put("orderNo",String.valueOf(order.getOrderNo()));
        //order ！= null 将数据放入map 中String.valueOf(order.getOrderNo()) 通过string转换
        resultMap.put("orderNo", String.valueOf(order.getOrderNo()));
        //组装生成支付宝订单的各种参数

        *//*(必填) 商户网站订单系统中唯一订单号，64个字符以内，只能包含字母、数字、下划线，
       需保证商户系统端不能重复，建议通过数据库sequence生成，
        String outTradeNo =   "tradeprecreate" + System.currentTimeMillis() + (long) (Math.random() * 10000000L);
        改成自己项目的订单好 如String outTradeNo = order.getOrderNo().toString();*//*

        String outTradeNo = order.getOrderNo().toString();

        *//*(必填) 订单标题，粗略描述用户的支付目的。如“xxx品牌xxx门店当面付扫码消费”
 *   String subject = "xxx品牌xxx门店当面付扫码消费";
 *改未自己使用瓶装 String subject = new StringBuilder()
 * .append("happymmall，订单号：").append(outTradeNo).toString();的方法
 *   *//*
        String subject = new StringBuilder().append("happymmall，订单号：").append(outTradeNo).toString();

        *//*(必填) 订单总金额，单位为元，不能超过1亿元
        如果同时传入了【打折金额】,【不可打折金额】,【订单总金额】三者,则必须满足如下条件:【订单总金额】=【打折金额】+【不可打折金额】
         String totalAmount = "0.01"; 考虑到一期没有打折选项
         直接使用order中的数据order.getPayment().toString()

        *//*
        String totalAmount = order.getPayment().toString();

        *//*(可选) 订单不可打折金额，可以配合商家平台配置折扣活动，如果酒水不参与打折，则将对应金额填写至此字段
        如果该值未传入,但传入了【订单总金额】,【打折金额】,则该值默认为【订单总金额】-【打折金额】
       *//*
        String undiscountableAmount = "0";

        // 卖家支付宝账号ID，用于支持一个签约账号下支持打款到不同的收款账号，(打款到sellerId对应的支付宝账号)
        // 如果该字段为空，则默认为与支付宝签约的商户的PID，也就是appid对应的PID
        String sellerId = "";

        *//*订单描述，可以对交易或商品进行一个详细地描述，比如填写"购买商品2件共15.00元"
 *  String body = "购买商品3件共20.00元";
 *  使用 new StringBuilder().append("订单：").append(outTradeNo).
 *  append(" 购买商品共：").
 *  append(totalAmount).append("元").toString()来计算
 * *//*
        String body = new StringBuilder().append("订单：").append(outTradeNo).append(" 购买商品共：").append(totalAmount).append("元").toString();

        // 商户操作员编号，添加此参数可以为商户操作员做销售统计
        String operatorId = "test_operator_id";

        // (必填) 商户门店编号，通过门店号和商家后台可以配置精准到门店的折扣信息，详询支付宝技术支持
        String storeId = "test_store_id";

        // 业务扩展参数，目前可添加由支付宝分配的系统商编号(通过setSysServiceProviderId方法)，详情请咨询支付宝技术支持
        ExtendParams extendParams = new ExtendParams();
        extendParams.setSysServiceProviderId("2088100200300400500");

        // 支付超时，定义为120分钟
        String timeoutExpress = "120m";

        // 商品明细列表，需填写购买商品详细信息，
        List<GoodsDetail> goodsDetailList = new ArrayList<GoodsDetail>();
        *//*1.查询订单字表  即 交易信息表 中该次交易的所有商品信息
 *List<OrderItem> orderItems =orderItemMapper.getByOrderNoUserId(orderNo,userId);
 * 2.循环遍历 将信息放入支付宝的商品信息中 而后添加到 支付宝的商品明细列表 及List<GoodsDetail> goodsDetailList
 * = new ArrayList<GoodsDetail>();里面
 * for (OrderItem orderItem : orderItemList) { GoodsDetail goods1 = GoodsDetail.newInstance(orderItem.getProductId().toString(), orderItem.getProductName(),BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(), new Double(100).doubleValue()).longValue(), orderItem.getQuantity());goodsDetailList.add(goods1); }
 * * *//*
        //1
        List<OrderItem> orderItemList = orderItemMapper.getByOrderNoUserId(orderNo, userId);
        //2 getCurrentUnitPrice 单价  BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(),new Double(100).doubleValue()).longValue()使用乘法计算商品单价并且将分转换成元
        for (OrderItem orderItem : orderItemList) {
            GoodsDetail goods1 = GoodsDetail.newInstance(orderItem.getProductId().toString(), orderItem.getProductName(),
                    BigDecimalUtil.mul(orderItem.getCurrentUnitPrice().doubleValue(), new Double(100).doubleValue()).longValue(),
                    orderItem.getQuantity());
            goodsDetailList.add(goods1);
        }


     *//*   // 创建一个商品信息，参数含义分别为商品id（使用国标）、名称、单价（单位为分）、数量，如果需要添加商品类别，详见GoodsDetail
        GoodsDetail goods1 = GoodsDetail.newInstance("goods_id001", "xxx小面包", 1000, 1);
        // 创建好一个商品后添加至商品明细列表
        goodsDetailList.add(goods1);

        // 继续创建并添加第一条商品信息，用户购买的产品为“黑人牙刷”，单价为5.00元，购买了两件
        GoodsDetail goods2 = GoodsDetail.newInstance("goods_id002", "xxx牙刷", 500, 2);
        goodsDetailList.add(goods2);


*//*
        // 创建扫码支付请求builder，设置请求参数 支付宝生产
        AlipayTradePrecreateRequestBuilder builder = new AlipayTradePrecreateRequestBuilder()
                .setSubject(subject).setTotalAmount(totalAmount).setOutTradeNo(outTradeNo)
                .setUndiscountableAmount(undiscountableAmount).setSellerId(sellerId).setBody(body)
                .setOperatorId(operatorId).setStoreId(storeId).setExtendParams(extendParams)
                .setTimeoutExpress(timeoutExpress)
                .setNotifyUrl(PropertiesUtil.getProperty("alipay.callback.url"))//支付宝服务器主动通知商户服务器里指定的页面http路径,根据需要设置
                .setGoodsDetailList(goodsDetailList);

        // 简单打印应答


        AlipayTradeService tradeService = new AlipayTradeServiceImpl.ClientBuilder().build();

        AlipayF2FPrecreateResult result = tradeService.tradePrecreate(builder);

        switch (result.getTradeStatus()) {
            case SUCCESS:
                logger.info("支付宝预下单成功: )");

                AlipayTradePrecreateResponse response = result.getResponse();
                dumpResponse(response);
                *//**
 * 二维码图片路径
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 * <p>
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 *//*
                File folder = new File(path);
                //如果该路径不存在
                if (!folder.exists()) {
                    //生成一个目录
                    folder.setWritable(true);
                    folder.mkdirs();
                }

                *//**
 * 根据外部订单号生成路径， 路径生成二维码
 * new 一个文件名
 *//*
                // 需要修改为运行机器上的路径
                //细节 /  生成二维码的路径qrPath  要生成的文件名qrFileName
                String qrPath = String.format(path + "/qr-%s.png", response.getOutTradeNo());

                String qrFileName = String.format("qr-%s.png", response.getOutTradeNo());

                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, qrPath);
                File targetFile = new File(path, qrFileName);

                try {
                    FTPUtil.uploadFile(Lists.newArrayList(targetFile));
                } catch (IOException e) {
                    logger.error("上传二维码异常" + e);
                    e.printStackTrace();
                }

                logger.info("qrPath:" + qrPath);
                //                ZxingUtils.getQRCodeImge(response.getQrCode(), 256, filePath);
                String qrUrl = PropertiesUtil.getProperty("ftp.server.http.prefix")+targetFile.getName();
                //将二维码放入map
                resultMap.put("qrUrl", qrUrl);

                return ServerResponse.createBySuccess(resultMap);
            case FAILED:
                logger.error("支付宝预下单失败!!!");
                return  ServerResponse.createByErrorMessage("支付宝预下单失败!!!");

            case UNKNOWN:
                logger.error("系统异常，预下单状态未知!!!");
                return  ServerResponse.createByErrorMessage("系统异常，预下单状态未知!!!");

            default:
                logger.error("不支持的交易状态，交易返回异常!!!");
                return  ServerResponse.createByErrorMessage("不支持的交易状态，交易返回异常!!!");
        }
    }

    // 简单打印应答
    private void dumpResponse(AlipayResponse response) {
        if (response != null) {
            logger.info(String.format("code:%s, msg:%s", response.getCode(), response.getMsg()));
            if (StringUtils.isNotEmpty(response.getSubCode())) {
                logger.info(String.format("subCode:%s, subMsg:%s", response.getSubCode(),
                        response.getSubMsg()));
            }
            logger.info("body:" + response.getBody());
        }
    }

       public ServerResponse aliCallvack(Map<String, String> params) {

        //外部订单号
        // region拿出订单号
//        Long orderNo=Long.parseLong(params.get("out_trade_no"));
        //endregion
        Long orderNo = Long.parseLong(params.get("out_trade_no"));
        // region 支付宝的交易号
        //   String tradeNo=params.get("trade_no");
//endregion
        String tradeNo = params.get("trade_no");
        // region  交易状态
        // String tradeStatus= params.get("trade_status");
        //endregion
        String tradeStatus = params.get("trade_status");
//查询内部订单号
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return ServerResponse.createByErrorMessage("非此商城订单，回调忽略");
        }
        //判断订单的交易状态
        if (order.getStatus() >= Const.OrderStatusEnum.PAID.getCode()) {
            return ServerResponse.createBySuccess("支付宝重复调用");
        }

        //判断状态是否交易成功
        if (Const.AlipayCallback.TRADE_STATUS_TRADE_SUCCESS.equals(tradeStatus)) {
          //跟新时间
            order.setPaymentTime(DateTimeUtil.strToDate(params.get("gmt_payment")));

            //跟新订单状态 改成已付款
            order.setStatus(Const.OrderStatusEnum.PAID.getCode());
            orderMapper.updateByPrimaryKeySelective(order);
        }
        //组装payinfo对象
        PayInfo payInfo = new PayInfo();
        payInfo.setUserId(order.getUserId());
        payInfo.setOrderNo(order.getOrderNo());
        //使用的那个支付工具
        payInfo.setPayPlatform(Const.PayPlatformEnum.ALIPAY.getCode());
//支付宝的交易号
        payInfo.setPlatformNumber(tradeNo);
//状态
        payInfo.setPlatformStatus(tradeStatus);
        payInfoMapper.insert(payInfo);
        return ServerResponse.createBySuccess();
    }
}*/

//endregion






