package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.VoucherErrorOrder;
import com.hmdp.mapper.VoucherErrorOrderMapper;
import com.hmdp.service.IVoucherErrorOrderService;
import org.springframework.stereotype.Service;

@Service
public class VoucherErrorOrderServiceImpl extends ServiceImpl<VoucherErrorOrderMapper, VoucherErrorOrder> implements IVoucherErrorOrderService {
}
