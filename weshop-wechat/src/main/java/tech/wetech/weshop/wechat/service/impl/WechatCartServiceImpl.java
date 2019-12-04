package tech.wetech.weshop.wechat.service.impl;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wetech.weshop.common.query.Query;
import tech.wetech.weshop.goods.api.GoodsApi;
import tech.wetech.weshop.goods.api.GoodsSpecificationApi;
import tech.wetech.weshop.goods.api.ProductApi;
import tech.wetech.weshop.goods.po.Goods;
import tech.wetech.weshop.goods.po.Product;
import tech.wetech.weshop.order.api.CartApi;
import tech.wetech.weshop.order.po.Cart;
import tech.wetech.weshop.user.api.AddressApi;
import tech.wetech.weshop.user.api.RegionApi;
import tech.wetech.weshop.user.api.UserCouponApi;
import tech.wetech.weshop.user.po.Address;
import tech.wetech.weshop.user.po.User;
import tech.wetech.weshop.user.po.UserCoupon;
import tech.wetech.weshop.wechat.enums.WeshopWechatResultStatus;
import tech.wetech.weshop.wechat.exception.WeshopWechatException;
import tech.wetech.weshop.wechat.service.WechatCartService;
import tech.wetech.weshop.wechat.utils.JwtHelper;
import tech.wetech.weshop.wechat.vo.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WechatCartServiceImpl implements WechatCartService {

    @Autowired
    private CartApi cartApi;

    @Autowired
    private GoodsApi goodsApi;

    @Autowired
    private ProductApi productApi;

    @Autowired
    private GoodsSpecificationApi goodsSpecificationApi;

    @Autowired
    private AddressApi addressApi;

    @Autowired
    private RegionApi regionApi;

    @Autowired
    private UserCouponApi userCouponApi;

    @Override
    public CartResultVO getCart() {
        User userInfo = JwtHelper.getUserInfo();
        Claims currentClaims = JwtHelper.getCurrentClaims();
        Cart query = new Cart();
        query.setUserId(userInfo.getId());
        query.setSessionId(currentClaims.getId());
        List<Cart> cartList = cartApi.queryList(query).getData();
        CartResultVO.CartTotalVO cartTotalVO = new CartResultVO.CartTotalVO();

        Integer goodsCount = 0;
        BigDecimal goodsAmount = BigDecimal.ZERO;
        Integer checkedGoodsCount = 0;
        BigDecimal checkedGoodsAmount = BigDecimal.ZERO;
        for (Cart cart : cartList) {
            goodsCount += cart.getNumber();
            //goodsAmount = goodsAmount + retailPrice * number
            goodsAmount = goodsAmount.add(
                cart.getRetailPrice().multiply(new BigDecimal(cart.getNumber()))
            );
            if (cart.getChecked()) {
                checkedGoodsCount += cart.getNumber();
                //checkedGoodsAmount = checkedGoodsAmount + retailPrice * number
                checkedGoodsAmount = checkedGoodsAmount.add(
                    cart.getRetailPrice().multiply(new BigDecimal(cart.getNumber())));
            }
        }

        cartTotalVO.setGoodsCount(goodsCount)
            .setGoodsAmount(goodsAmount)
            .setCheckedGoodsCount(checkedGoodsCount)
            .setCheckedGoodsAmount(checkedGoodsAmount);

        return new CartResultVO(cartList, cartTotalVO);
    }

    @Override
    public void deleteCartGoods(CartGoodsDeleteVO deleteVO) {
        List<Integer> productIds = Arrays.stream(deleteVO.getProductIds().split(",")).map(Integer::valueOf).collect(Collectors.toList());
        Query<Cart> query = new Query<>();
        query.setSelects(Cart::getId).andIn(Cart::getProductId, productIds);
        cartApi.queryByCondition(query).getData().stream()
            .map(Cart::getId)
            .forEach(cartId -> cartApi.deleteById(cartId));
    }

    @Override
    public void checkedCartGoods(CartCheckedVO cartCheckedVO) {
        List<Integer> productIds = Arrays.stream(cartCheckedVO.getProductIds().split(",")).map(Integer::valueOf).collect(Collectors.toList());
        Query<Cart> query = new Query<>();
        query.setSelects(Cart::getId).andIn(Cart::getProductId, productIds);
        cartApi.queryByCondition(query).getData().stream()
            .map(Cart::getId)
            .forEach(cartId -> {
                Cart updateCart = new Cart();
                updateCart.setChecked(cartCheckedVO.getChecked());
                updateCart.setId(cartId);
                cartApi.updateNotNull(updateCart);
            });
    }

    @Override
    @Transactional
    public void addGoodsToCart(CartParamVO cartParamDTO) {
        User userInfo = JwtHelper.getUserInfo();
        Claims currentClaims = JwtHelper.getCurrentClaims();
        Goods goods = goodsApi.queryById(cartParamDTO.getGoodsId()).getData();
        if (goods == null || goods.getDelete()) {
            //商品已下架
            throw new WeshopWechatException(WeshopWechatResultStatus.GOODS_HAVE_BEEN_TAKEN_OFF_THE_SHELVES);
        }
        Product queryProduct = new Product();
        queryProduct.setGoodsId(cartParamDTO.getGoodsId());
        queryProduct.setId(cartParamDTO.getProductId());
        Product product = productApi.queryOne(queryProduct).getData();
        if (product == null || product.getGoodsNumber() < cartParamDTO.getNumber()) {
            //库存不足
            throw new WeshopWechatException(WeshopWechatResultStatus.UNDER_STOCK);
        }
        Cart query = new Cart();
        query.setGoodsId(cartParamDTO.getGoodsId());
        query.setProductId(cartParamDTO.getProductId());
        Cart cart = cartApi.queryOne(query).getData();
        if (cart == null) {
            // 判断购物车中是否存在此规格商品
            List<String> goodsSpecificationValueList = new LinkedList<>();
            if (product.getGoodsSpecificationIds() != null) {
                List<Integer> specificationIdList = Arrays.stream(product.getGoodsSpecificationIds().split("_"))
                    .filter(id -> id.length() > 0)
                    .map(Integer::valueOf)
                    .collect(Collectors.toList());
                if (!specificationIdList.isEmpty()) {
                    goodsSpecificationValueList = goodsSpecificationApi.queryValueByGoodsIdAndIdIn(cartParamDTO.getGoodsId(), specificationIdList).getData();
                }
            }
            Cart cartData = new Cart();
            cartData.setGoodsId(cartParamDTO.getGoodsId());
            cartData.setProductId(cartParamDTO.getProductId());
            cartData.setGoodsSn(product.getGoodsSn());
            cartData.setGoodsName(goods.getName());
            cartData.setListPicUrl(goods.getListPicUrl());
            cartData.setNumber(cartParamDTO.getNumber().shortValue());
            cartData.setSessionId(currentClaims.getId());
            cartData.setUserId(userInfo.getId());
            cartData.setRetailPrice(product.getRetailPrice());
            cartData.setMarketPrice(product.getRetailPrice());
            cartData.setGoodsSpecificationNameValue(
                goodsSpecificationValueList.stream()
                    .collect(Collectors.joining(";"))
            );
            cartData.setGoodsSpecificationIds(product.getGoodsSpecificationIds());
            cartData.setChecked(true);
            cartApi.create(cartData);
        } else {
            // 如果已经存在购物车中，则数量增加
            if (product.getGoodsNumber() < (cartParamDTO.getNumber() + cart.getNumber())) {
                throw new WeshopWechatException(WeshopWechatResultStatus.UNDER_STOCK);
            }
            Cart updateCart = new Cart();
            updateCart.setNumber(cartParamDTO.getNumber().shortValue());
            updateCart.setId(cart.getId());
            cartApi.updateNotNull(updateCart);
        }

    }

    @Override
    @Transactional
    public void updateGoods(CartParamVO cartParamDTO) {
        User userInfo = JwtHelper.getUserInfo();
        Claims currentClaims = JwtHelper.getCurrentClaims();
        // 取得规格的信息,判断规格库存
        Product queryProduct = new Product();
        queryProduct.setGoodsId(cartParamDTO.getProductId());
        queryProduct.setId(cartParamDTO.getProductId());
        Product product = productApi.queryOne(queryProduct).getData();
        if (product == null || product.getGoodsNumber() < cartParamDTO.getNumber()) {
            //库存不足
            throw new WeshopWechatException(WeshopWechatResultStatus.UNDER_STOCK);
        }
        // 判断是否已经存在product_id购物车商品
        Cart cart = cartApi.queryById(cartParamDTO.getId()).getData();
        if (cart.getProductId().equals(cartParamDTO.getProductId())) {
            Cart updateCart = new Cart();
            updateCart.setNumber(cartParamDTO.getNumber().shortValue());
            updateCart.setId(cartParamDTO.getId());
            // 只是更新number
            cartApi.updateNotNull(updateCart);
            return;
        }
        Cart queryCart = new Cart();
        queryCart.setUserId(userInfo.getId());
        queryCart.setSessionId(currentClaims.getId());
        queryCart.setGoodsId(cartParamDTO.getGoodsId());
        queryCart.setGoodsId(cartParamDTO.getGoodsId());
        queryCart.setProductId(cartParamDTO.getProductId());
        Cart newCartInfo = cartApi.queryOne(queryCart).getData();
        if (newCartInfo == null) {
            //直接更新原来的cartInfo
            // 判断购物车中是否存在此规格商品
            List<String> goodsSpecificationValueList = new LinkedList<>();
            if (product.getGoodsSpecificationIds() != null) {
                List<Integer> specificationIdList = Arrays.stream(product.getGoodsSpecificationIds()
                    .split("_"))
                    .filter(id -> id.length() > 0)
                    .map(Integer::valueOf)
                    .collect(Collectors.toList());
                if (!specificationIdList.isEmpty()) {
                    goodsSpecificationValueList = goodsSpecificationApi.queryValueByGoodsIdAndIdIn(cartParamDTO.getGoodsId(), specificationIdList).getData();
                }
            }
            Cart cartData = new Cart();
            cartData.setId(cartParamDTO.getId());
            cartData.setNumber(cartParamDTO.getNumber().shortValue());
            cartData.setGoodsSpecificationNameValue(
                goodsSpecificationValueList.stream()
                    .collect(Collectors.joining(";"))
            );
            cartData.setGoodsSpecificationIds(product.getGoodsSpecificationIds());
            cartData.setRetailPrice(product.getRetailPrice());
            cartData.setMarketPrice(product.getRetailPrice());
            cartData.setProductId(cartParamDTO.getProductId());
            cartData.setGoodsSn(product.getGoodsSn());
            cartApi.updateNotNull(cartData);
        } else {
            // 合并购物车已有的product信息，删除已有的数据
            Integer newNumber = cartParamDTO.getNumber() + newCartInfo.getNumber();
            if (product == null || product.getGoodsNumber() < newNumber) {
                //库存不足
                throw new WeshopWechatException(WeshopWechatResultStatus.UNDER_STOCK);
            }
            cartApi.deleteById(newCartInfo.getId());
            Cart cartData = new Cart();

            cartData.setId(cartParamDTO.getId());
            cartData.setNumber(newNumber.shortValue());
            cartData.setGoodsSpecificationNameValue(newCartInfo.getGoodsSpecificationNameValue());
            cartData.setGoodsSpecificationIds(newCartInfo.getGoodsSpecificationIds());
            cartData.setRetailPrice(product.getRetailPrice());
            cartData.setMarketPrice(product.getRetailPrice());
            cartData.setProductId(cartParamDTO.getProductId());
            cartData.setGoodsSn(product.getGoodsSn());
            cartApi.updateNotNull(cartData);
        }


    }

    @Override
    public CartCheckoutVO checkoutCart(Integer addressId, Integer couponId) {
        User userInfo = JwtHelper.getUserInfo();
        CartCheckoutVO cartCheckoutDTO = new CartCheckoutVO();
        //选择收货地址
        Address checkedAddress = null;
        if (addressId != null) {
            Address queryAddress = new Address();
            queryAddress.setId(addressId);
            queryAddress.setUserId(userInfo.getId());
            checkedAddress = addressApi.queryOne(queryAddress).getData();
        } else {
            Address queryAddress = new Address();
            queryAddress.setUserId(userInfo.getId());
            queryAddress.setIsDefault(true);
            checkedAddress = addressApi.queryOne(queryAddress).getData();
        }

        CartCheckoutVO.CheckedAddressVO checkedAddressVO = null;
        if (checkedAddress != null) {
            checkedAddressVO = new CartCheckoutVO.CheckedAddressVO(checkedAddress)
                .setProvinceName(
                    regionApi.queryNameById(checkedAddress.getProvinceId()).getData()
                )
                .setCityName(
                    regionApi.queryNameById(checkedAddress.getCityId()).getData()
                )
                .setDistrictName(
                    regionApi.queryNameById(checkedAddress.getDistrictId()).getData()
                );

            checkedAddressVO.setFullRegion(
                checkedAddressVO.getProvinceName() + checkedAddressVO.getCityName() + checkedAddressVO.getDistrictName()
            );
        }
        // 根据收货地址计算运费，未实现
        BigDecimal freightPrice = BigDecimal.ZERO;

        CartResultVO cartData = this.getCart();
        List<Cart> checkedGoodsList = cartData.getCartList().stream()
            .filter(Cart::getChecked)
            .collect(Collectors.toList());

        // 获取可用的优惠券信息
        List<UserCoupon> userCouponList = userCouponApi.queryList(new UserCoupon() {{
            setUserId(userInfo.getId());
        }}).getData();
        BigDecimal couponPrice = BigDecimal.ZERO;

        //计算订单的费用

        //商品总价
        BigDecimal goodsTotalPrice = cartData.getCartTotal().getCheckedGoodsAmount();
        BigDecimal orderTotalPrice = cartData.getCartTotal().getCheckedGoodsAmount()
            .add(freightPrice)
            .subtract(couponPrice);

        //减去其它支付的金额后，要实际支付的金额
        BigDecimal actualPrice = orderTotalPrice
            .subtract(new BigDecimal(0.00));

        cartCheckoutDTO.setCheckedAddress(checkedAddressVO);
        cartCheckoutDTO.setFreightPrice(freightPrice);
        cartCheckoutDTO.setCheckedCoupon(null);
        cartCheckoutDTO.setCouponList(userCouponList);
        cartCheckoutDTO.setCouponPrice(couponPrice);
        cartCheckoutDTO.setCheckedGoodsList(checkedGoodsList);
        cartCheckoutDTO.setGoodsTotalPrice(goodsTotalPrice);
        cartCheckoutDTO.setOrderTotalPrice(orderTotalPrice);
        cartCheckoutDTO.setActualPrice(actualPrice);
        return cartCheckoutDTO;
    }
}
