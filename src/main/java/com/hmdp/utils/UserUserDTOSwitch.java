package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

import java.util.List;
import java.util.stream.Collectors;

public class UserUserDTOSwitch {
    public static List<UserDTO> getUserDTOS(List<User> users) {
        List<UserDTO> userDTOS = users.stream().map(user -> {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setNickName(user.getNickName());
            userDTO.setIcon(user.getIcon());
            return userDTO;
        }).collect(Collectors.toList());
        return userDTOS;
    }
}
