import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ArmyList } from './models';

@Injectable({ providedIn: 'root' })
export class ArmiesService {
  private http = inject(HttpClient);

  importRosz(file: File): Observable<ArmyList> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ArmyList>('/api/armies/import', formData);
  }
}
