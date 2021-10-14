/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 20.11.2005 by RST.
// $Id: GameItemList.java,v 1.2 2006-01-21 21:53:32 salomo Exp $

package jake2.game;


import jake2.game.items.gitem_t;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class GameItemList {

    public final gitem_t[] itemlist;

    /**
     * Initialize the items list using a csv table from the classpath (absolute)
     *
     * @param tableName - classpath resource to the item csv table
     */
    public GameItemList(String tableName) {

        try (InputStream in = GameItemList.class.getResourceAsStream(tableName)) {
            final CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true).build();
            CSVParser source = CSVParser.parse(in, StandardCharsets.UTF_8, format);
            AtomicInteger index = new AtomicInteger();

            final List<gitem_t> itemList = source.stream()
                    .map(strings -> gitem_t.readFromCsv(strings, index.getAndIncrement()))
                    .collect(Collectors.toList());

            itemlist = itemList.toArray(new gitem_t[]{});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
