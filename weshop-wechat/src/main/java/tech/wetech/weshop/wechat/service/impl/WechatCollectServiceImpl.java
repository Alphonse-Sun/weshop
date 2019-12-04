package tech.wetech.weshop.wechat.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tech.wetech.weshop.common.query.Query;
import tech.wetech.weshop.user.api.CollectApi;
import tech.wetech.weshop.user.dto.GoodsCollectDTO;
import tech.wetech.weshop.user.po.Collect;
import tech.wetech.weshop.user.po.User;
import tech.wetech.weshop.wechat.service.WechatCollectService;
import tech.wetech.weshop.wechat.utils.JwtHelper;
import tech.wetech.weshop.wechat.vo.CollectAddOrDeleteParamVO;
import tech.wetech.weshop.wechat.vo.CollectAddOrDeleteResultVO;

import java.util.List;

@Service
public class WechatCollectServiceImpl implements WechatCollectService {

    @Autowired
    private CollectApi collectApi;

    @Override
    public CollectAddOrDeleteResultVO addOrDelete(CollectAddOrDeleteParamVO dto) {
        User userInfo = JwtHelper.getUserInfo();
        Query<Collect> query = new Query<>();
        query.andEqualTo(Collect::getTypeId, dto.getTypeId())
            .andEqualTo(Collect::getValueId, dto.getValueId())
            .andEqualTo(Collect::getUserId, userInfo.getId());
        List<Collect> data = collectApi.queryByCondition(query).getData();
        //添加收藏
        if (data.size() == 0) {
            Collect collect = new Collect();
            collect.setTypeId(dto.getTypeId());
            collect.setValueId(dto.getValueId());
            collect.setUserId(userInfo.getId());
            collectApi.create(collect);
            return new CollectAddOrDeleteResultVO(true);
        } else {
            Collect collect = new Collect();
            collect.setTypeId(dto.getTypeId());
            collect.setValueId(dto.getValueId());
            collect.setUserId(userInfo.getId());
            collectApi.create(collect);
            collectApi.delete(collect);
            return new CollectAddOrDeleteResultVO(false);
        }

    }

    @Override
    public List<GoodsCollectDTO> queryGoodsCollectList() {
        User userInfo = JwtHelper.getUserInfo();
        return collectApi.queryGoodsCollectList(userInfo.getId()).getData();
    }
}
