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

package natalia.dymnikova.cluster.scheduler.impl.find.optimal;

import akka.actor.Address;

import java.util.List;

/**
 * Created by dyma on 06.05.16.
 */
public interface GroupOfAddresses {
    List<Group> getGroups();
    Group getGroup(final Address address);
    long getDistance(final Group group1, final Group group2);

    class Group {
        private List<Address> addresses;

        public Group(final List<Address> addresses) {
            this.addresses = addresses;
        }

        public List<Address> getAddress() {
            return addresses;
        }

        public boolean contains(final Address address) {
            return getAddress().contains(address);
        }

        public void addGroup(final Address address) {
            addresses.add(address);
        }

    }
}
