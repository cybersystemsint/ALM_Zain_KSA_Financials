/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tbNetworkDevice;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface NetworkDeviceService {

    tbNetworkDevice findBySerialNumber(String serialNumber);

}
