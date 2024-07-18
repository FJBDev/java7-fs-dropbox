/*******************************************************************************
 * Copyright (C) 2020 Frédéric Bard
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *******************************************************************************/
package com.github.fge.fs.dropbox.driver;

public class ListFolderLongpollResult {

   private boolean _changes;
   private Long    _backoff;

   public ListFolderLongpollResult(final boolean changes, final Long backoff) {
      _changes = changes;
      _backoff = backoff;
   }

   public Long getBackoff() {
      return _backoff;
   }

   public boolean getChanges() {
      return _changes;
   }

   public void setBackoff(final Long backoff) {
      this._backoff = backoff;
   }

   public void setChanges(final boolean changes) {
      this._changes = changes;
   }
}
