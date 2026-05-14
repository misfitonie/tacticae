import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ComputeRequest, ComputeResponse } from './models';

@Injectable({ providedIn: 'root' })
export class StatsService {
  private http = inject(HttpClient);

  compute(req: ComputeRequest): Observable<ComputeResponse> {
    return this.http.post<ComputeResponse>('/api/stats/compute', req);
  }
}
