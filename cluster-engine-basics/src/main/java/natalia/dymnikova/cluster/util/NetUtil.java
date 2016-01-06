// Copyright (c) 2016 Natalia Dymnikova
// Available via the MIT license
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the "Software"), to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
// and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
// TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
// CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
// OR OTHER DEALINGS IN THE SOFTWARE.

package natalia.dymnikova.cluster.util;

import com.google.common.net.InetAddresses;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static natalia.dymnikova.util.MoreThrowables.propagateUnchecked;

/**
 * 
 */
public class NetUtil {

    public static void main(final String[] args) {
        networkInterfaces()
            .collect(toList())
            .forEach(System.out::println);

        externalAddresses()
            .map(InetAddress::getAddress)
            .map(NetUtil::getByAddress)
            .map(InetAddress::getHostAddress)
            .collect(toList())
            .forEach(System.out::println);
    }

    public static InetAddress getByAddress(final byte[] b) {
        try {
            return InetAddress.getByAddress(b);
        } catch (final UnknownHostException e) {
            throw propagateUnchecked(e);
        }
    }

    public static Stream<InetAddress> externalAddresses() {
        return networkInterfaces()
            .map(NetworkInterface::getInterfaceAddresses)
            .flatMap(Collection::stream)
            .map(InterfaceAddress::getAddress)
            ;
    }

    public static boolean isLoopback(final NetworkInterface iface) {
        try {
            return iface.isLoopback();
        } catch (final SocketException e) {
            throw propagateUnchecked(e);
        }
    }

    public static Stream<NetworkInterface> networkInterfaces() {
        try {
            return Collections
                .list(NetworkInterface.getNetworkInterfaces())
                .stream()
                .filter(((Predicate<NetworkInterface>) NetUtil::isLoopback).negate())
                .filter(NetUtil::isUp)
                ;
        } catch (final IOException e) {
            throw propagateUnchecked(e);
        }
    }

    public static boolean isUp(final NetworkInterface iface) {
        try {
            return iface.isUp();
        } catch (final SocketException e) {
            throw propagateUnchecked(e);
        }
    }

}
